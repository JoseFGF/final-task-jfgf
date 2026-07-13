package com.fieldops.repository;

import com.fieldops.model.Order;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acceso a {@link Order}. Las queries de visibilidad por rol (FR-001 a
 * FR-003) se resuelven combinando {@link #findByAssignedTechnicianId(UUID)}
 * (TECHNICIAN) con {@link JpaRepository#findAll()} (DISPATCHER/SUPERVISOR,
 * que ven todas las órdenes del sistema); la decisión de cuál usar vive en
 * {@code OrderService}, no aquí.
 */
public interface OrderRepository extends JpaRepository<Order, UUID> {

  List<Order> findByAssignedTechnicianId(UUID technicianId);
}
