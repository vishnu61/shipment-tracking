package com.logistics.tracking.repository;

import com.logistics.tracking.model.Webhook;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WebhookRepository extends JpaRepository<Webhook, UUID> {
    List<Webhook> findByCompanyIdAndActiveTrue(UUID companyId);
    Optional<Webhook> findByIdAndCompanyId(UUID id, UUID companyId);
    List<Webhook> findAllByActiveTrue();
}
