package com.fieldops.repository;

import com.fieldops.model.Order;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acceso a {@link Order}. Las queries de visibilidad por rol (FR-001 a
 * FR-003) se resuelven combinando {@link #findByAssignedTechnicianId(UUID)}
 * (TECHNICIAN) con {@link #findAll()} (DISPATCHER/SUPERVISOR, que ven todas
 * las órdenes del sistema); la decisión de cuál usar vive en
 * {@code OrderService}, no aquí.
 *
 * <p>Ambas queries fuerzan la carga de {@code assignedTechnician} vía
 * {@link EntityGraph} (feature 003, code review): desde que
 * {@code OrderSummaryResponse} expone {@code assignedTechnicianEmail} en vez
 * del UUID, leer ese campo inicializa el proxy lazy de {@code User} — sin
 * este fetch adjunto, el listado de órdenes disparaba una query N+1 (una
 * por cada orden con technician asignado).
 */
public interface OrderRepository extends JpaRepository<Order, UUID> {

  @Override
  @EntityGraph(attributePaths = "assignedTechnician")
  List<Order> findAll();

  @EntityGraph(attributePaths = "assignedTechnician")
  List<Order> findByAssignedTechnicianId(UUID technicianId);
}
