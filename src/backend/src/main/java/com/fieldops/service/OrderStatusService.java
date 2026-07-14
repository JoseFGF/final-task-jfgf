package com.fieldops.service;

import com.fieldops.dto.OrderDetailResponse;
import com.fieldops.exception.ConflictException;
import com.fieldops.exception.ForbiddenException;
import com.fieldops.exception.NotFoundException;
import com.fieldops.exception.ValidationException;
import com.fieldops.model.Order;
import com.fieldops.model.OrderStatus;
import com.fieldops.model.Role;
import com.fieldops.repository.OrderRepository;
import com.fieldops.security.CurrentUser;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cambio de estado de una orden (004-order-state-transitions, ADR-009): un
 * único endpoint/servicio para dos historias de usuario con reglas de rol
 * distintas.
 *
 * <ul>
 *   <li><b>TECHNICIAN</b> (US1, FR-001 a FR-003): únicamente {@code assigned
 *       → in_progress}, y solo si es el technician actualmente asignado a esa
 *       orden ("iniciar trabajo").
 *   <li><b>DISPATCHER</b>/<b>SUPERVISOR</b> (US2, FR-004 a FR-006): cualquier
 *       par de estados adyacentes (ADR-010), en cualquier dirección salvo
 *       {@code pending_review → closed} (un solo sentido); exige evidencia
 *       mínima antes de {@code pending_review} (FR-005) y desvincula al
 *       technician al retroceder hasta {@code draft} (data-model.md).
 * </ul>
 *
 * <p>Ante cambios de estado concurrentes sobre la misma orden (FR-008), se
 * reutiliza exactamente el mismo patrón de bloqueo optimista + reintento con
 * auto-referencia {@code @Lazy} ya usado por {@link ReassignmentService}
 * (ADR-011).
 */
@Service
public class OrderStatusService {

  /**
   * Tabla de adyacencia (ADR-010, data-model.md): para cada estado de origen,
   * el conjunto de destinos alcanzables en una única transición reconocida.
   * {@code pending_review → closed} es de un solo sentido (no hay entrada
   * {@code closed → pending_review}); {@code closed} nunca es origen (mapa
   * vacío, FR-006).
   */
  private static final Map<OrderStatus, Set<OrderStatus>> ADJACENCY = buildAdjacency();

  private static final int MAX_ATTEMPTS = 5;

  private final OrderRepository orderRepository;
  private final OrderStatusService self;

  public OrderStatusService(OrderRepository orderRepository, @Lazy OrderStatusService self) {
    this.orderRepository = orderRepository;
    // Auto-referencia perezosa (proxy de Spring), mismo motivo que
    // ReassignmentService: doChangeStatus() debe pasar por el interceptor de
    // @Transactional también en la re-invocación de cada reintento.
    this.self = self;
  }

  public OrderDetailResponse changeStatus(
      CurrentUser currentUser, UUID orderId, OrderStatus targetStatus) {
    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      try {
        return self.doChangeStatus(currentUser, orderId, targetStatus);
      } catch (OptimisticLockingFailureException retryable) {
        if (attempt == MAX_ATTEMPTS) {
          throw retryable;
        }
        // Otro cambio de estado concurrente ganó esta ronda; se relee la
        // orden (versión más reciente) y se reintenta (FR-008).
      }
    }
    // Inalcanzable: el bucle siempre retorna o lanza en el último intento.
    throw new IllegalStateException("No se pudo completar el cambio de estado");
  }

  // @Transactional por intento (no en changeStatus()): cada intento debe leer
  // la orden con la versión más reciente confirmada y escribirla en su propia
  // transacción; envolver todo el bucle de reintentos en una única
  // transacción impediría que un reintento vea la versión escrita por el
  // cambio de estado concurrente que ganó la ronda anterior.
  @Transactional
  OrderDetailResponse doChangeStatus(CurrentUser currentUser, UUID orderId, OrderStatus targetStatus) {
    Order order =
        orderRepository
            .findById(orderId)
            .orElseThrow(() -> new NotFoundException("Orden no encontrada: " + orderId));

    switch (currentUser.role()) {
      case TECHNICIAN -> applyTechnicianTransition(order, currentUser.id(), targetStatus);
      case DISPATCHER, SUPERVISOR -> applyManualTransition(order, targetStatus);
      default ->
          throw new ForbiddenException(
              "El rol autenticado no puede cambiar el estado de una orden");
    }

    order.setStatus(targetStatus);
    Order saved = orderRepository.saveAndFlush(order);
    return OrderDetailResponse.from(saved);
  }

  // TECHNICIAN (FR-001 a FR-003, FR-007): única transición permitida,
  // "iniciar trabajo" (assigned -> in_progress), solo sobre la orden propia.
  // Pedir cualquier otro destino no es un problema de estado sino de rol: el
  // cambio manual de estado (cualquier destino distinto de in_progress) es
  // exclusivo de dispatcher/supervisor (FR-007) -> 403, no 409.
  private void applyTechnicianTransition(Order order, UUID technicianId, OrderStatus targetStatus) {
    if (targetStatus != OrderStatus.in_progress) {
      throw new ForbiddenException(
          "El cambio manual de estado es exclusivo de dispatcher/supervisor; el technician solo"
              + " puede iniciar el trabajo (assigned -> in_progress)");
    }
    boolean isAssignedToThisTechnician =
        order.getAssignedTechnician() != null
            && order.getAssignedTechnician().getId().equals(technicianId);
    if (!isAssignedToThisTechnician) {
      throw new ForbiddenException("La orden no está asignada a este technician");
    }
    if (order.getStatus() != OrderStatus.assigned) {
      throw new ConflictException(
          "El technician solo puede iniciar el trabajo (assigned -> in_progress)");
    }
  }

  // DISPATCHER/SUPERVISOR (FR-004 a FR-006): cualquier par adyacente, en
  // cualquier dirección salvo pending_review -> closed.
  private void applyManualTransition(Order order, OrderStatus targetStatus) {
    if (!ADJACENCY.getOrDefault(order.getStatus(), Set.of()).contains(targetStatus)) {
      throw new ConflictException(
          "Transición no reconocida: " + order.getStatus() + " -> " + targetStatus);
    }
    if (targetStatus == OrderStatus.pending_review && order.getEvidencePhotos().isEmpty()) {
      throw new ValidationException(
          "Se requiere al menos una foto de evidencia antes de pending_review");
    }
    if (targetStatus == OrderStatus.draft) {
      order.setAssignedTechnician(null);
    }
  }

  private static Map<OrderStatus, Set<OrderStatus>> buildAdjacency() {
    Map<OrderStatus, Set<OrderStatus>> adjacency = new EnumMap<>(OrderStatus.class);
    adjacency.put(OrderStatus.draft, Set.of(OrderStatus.assigned));
    adjacency.put(OrderStatus.assigned, Set.of(OrderStatus.draft, OrderStatus.in_progress));
    adjacency.put(OrderStatus.in_progress, Set.of(OrderStatus.assigned, OrderStatus.pending_review));
    adjacency.put(OrderStatus.pending_review, Set.of(OrderStatus.in_progress, OrderStatus.closed));
    adjacency.put(OrderStatus.closed, Set.of());
    return Map.copyOf(adjacency);
  }
}
