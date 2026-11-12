package com.example.analytics.repository;

import com.example.analytics.model.AuditEntry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditEntryRepository extends JpaRepository<AuditEntry, String> {

    List<AuditEntry> findAllByOrderByOccurredAtDesc(Pageable pageable);

    List<AuditEntry> findByActorIdOrderByOccurredAtDesc(String actorId, Pageable pageable);
}
