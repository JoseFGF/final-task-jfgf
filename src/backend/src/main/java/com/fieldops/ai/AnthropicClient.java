package com.fieldops.ai;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Cliente HTTP hacia la API de mensajes de Anthropic (Claude), usada para
 * generar el resumen de incidencia de US5 (research.md, ADR-001). La API key
 * se inyecta desde la variable de entorno {@code ANTHROPIC_API_KEY} (ver
 * `docker-compose.yml`), nunca hardcodeada.
 *
 * <p>El prompt de sistema instruye explícitamente al modelo a responder con
 * el token {@value #INSUFFICIENT_EVIDENCE_TOKEN} cuando la nota no alcance
 * para un resumen fiable (Principio V), pero este cliente no confía
 * únicamente en que el modelo obedezca: antes de llamar siquiera a la API,
 * comprueba en el propio código si la nota está vacía o en blanco, y en ese
 * caso devuelve directamente evidencia insuficiente sin gastar la llamada
 * (FR-016, T051).
 */
@Component
public class AnthropicClient {

  static final String INSUFFICIENT_EVIDENCE_TOKEN = "INSUFFICIENT_EVIDENCE";

  private static final String SYSTEM_PROMPT =
      "Resume brevemente la incidencia a partir de esta nota; si la nota no tiene "
          + "informacion suficiente para un resumen util, responde exactamente con el "
          + "token INSUFFICIENT_EVIDENCE y nada mas - no inventes contenido. Cuando sí "
          + "haya informacion suficiente, el resumen debe ser breve (unas pocas frases, "
          + "no una reescritura extensa) y debe estar escrito en el mismo idioma que la "
          + "nota original.";

  private final RestClient restClient;
  private final String model;

  public AnthropicClient(
      RestClient.Builder restClientBuilder,
      @Value("${anthropic.api-key:}") String apiKey,
      @Value("${anthropic.base-url:https://api.anthropic.com/v1/messages}") String baseUrl,
      @Value("${anthropic.model:claude-3-5-haiku-20241022}") String model,
      @Value("${anthropic.timeout-seconds:10}") long timeoutSeconds) {
    this.model = model;

    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    int timeoutMillis = (int) Duration.ofSeconds(timeoutSeconds).toMillis();
    requestFactory.setConnectTimeout(timeoutMillis);
    requestFactory.setReadTimeout(timeoutMillis);

    this.restClient =
        restClientBuilder
            .baseUrl(baseUrl)
            .requestFactory(requestFactory)
            .defaultHeader("x-api-key", apiKey)
            .defaultHeader("anthropic-version", "2023-06-01")
            .defaultHeader("content-type", "application/json")
            .build();
  }

  /**
   * Devuelve el resumen de la nota, o {@link Optional#empty()} si la nota está
   * vacía/en blanco o si el modelo declara evidencia insuficiente. Cualquier
   * fallo de red, timeout o error HTTP se propaga como excepción sin
   * capturar: es responsabilidad de quien orquesta la llamada (@code
   * IncidentSummaryService}) aplicar el fail-safe (Principio V) y traducirlo
   * a "evidencia insuficiente" en vez de un error 500.
   */
  public Optional<String> summarize(String executionNote) {
    if (executionNote == null || executionNote.isBlank()) {
      return Optional.empty();
    }

    AnthropicMessageResponse response =
        restClient
            .post()
            .body(buildRequestBody(executionNote))
            .retrieve()
            .body(AnthropicMessageResponse.class);

    String text = extractText(response);
    if (text == null || text.isBlank() || text.contains(INSUFFICIENT_EVIDENCE_TOKEN)) {
      return Optional.empty();
    }
    return Optional.of(text.trim());
  }

  private Map<String, Object> buildRequestBody(String executionNote) {
    return Map.of(
        "model", model,
        "max_tokens", 300,
        "system", SYSTEM_PROMPT,
        "messages", List.of(Map.of("role", "user", "content", executionNote)));
  }

  private String extractText(AnthropicMessageResponse response) {
    if (response == null || response.content() == null || response.content().isEmpty()) {
      return null;
    }
    return response.content().get(0).text();
  }

  /** Forma mínima de la respuesta de {@code POST /v1/messages} que este cliente necesita. */
  private record AnthropicMessageResponse(List<ContentBlock> content) {}

  private record ContentBlock(String type, String text) {}
}
