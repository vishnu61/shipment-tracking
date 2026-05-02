package com.logistics.tracking.repository;

import com.logistics.tracking.model.ShipmentEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShipmentEventRepository extends JpaRepository<ShipmentEvent, UUID> {

    Page<ShipmentEvent> findByShipmentIdAndCompanyIdOrderByEventTimestampDesc(
            String shipmentId, UUID companyId, Pageable pageable);

    @Query("SELECT e FROM ShipmentEvent e WHERE e.shipmentId = :shipmentId " +
           "AND e.companyId = :companyId ORDER BY e.eventTimestamp DESC LIMIT 1")
    Optional<ShipmentEvent> findLatestByShipmentIdAndCompanyId(
            @Param("shipmentId") String shipmentId,
            @Param("companyId") UUID companyId);

    boolean existsByShipmentIdAndCompanyId(String shipmentId, UUID companyId);
}
