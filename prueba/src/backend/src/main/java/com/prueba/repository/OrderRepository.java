package com.prueba.repository;

import com.prueba.model.Order;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio de {@link Order} para US1 (consulta de ordenes). `findAll` y
 * `findById` los aporta {@link JpaRepository}; solo se anade el metodo de
 * filtrado por technician asignado que exige FR-001.
 *
 * <p>Ambos metodos de listado cargan {@code assignedTechnician} en la misma
 * consulta (JOIN FETCH via {@link EntityGraph}): sin esto, cada orden de la
 * lista dispararia una consulta adicional al acceder al technician asignado
 * (N+1) en {@link com.prueba.dto.OrderSummaryResponse#from}.
 */
public interface OrderRepository extends JpaRepository<Order, UUID> {

  @Override
  @EntityGraph(attributePaths = "assignedTechnician")
  List<Order> findAll();

  @EntityGraph(attributePaths = "assignedTechnician")
  List<Order> findByAssignedTechnicianId(UUID technicianId);
}
