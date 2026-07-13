package com.fieldops.repository;

import com.fieldops.model.AccessAuditEntry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccessAuditEntryRepository extends JpaRepository<AccessAuditEntry, UUID> {

  List<AccessAuditEntry> findAllByAttemptedAction(String attemptedAction);
}
