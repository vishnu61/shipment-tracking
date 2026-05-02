package com.logistics.tracking.repository;

import com.logistics.tracking.model.Shipment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShipmentRepository extends JpaRepository<Shipment, UUID> {
    Optional<Shipment> findByShipmentIdAndCompanyId(String shipmentId, UUID companyId);
    boolean existsByShipmentIdAndCompanyId(String shipmentId, UUID companyId);
}
