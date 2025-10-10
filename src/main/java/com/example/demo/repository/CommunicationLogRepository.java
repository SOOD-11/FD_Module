package com.example.demo.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.demo.entities.CommunicationLog;
import com.example.demo.enums.CommunicationStatus;
import com.example.demo.enums.TemplateType;

@Repository
public interface CommunicationLogRepository extends JpaRepository<CommunicationLog, Long> {

    /**
     * Finds all communication logs for a specific customer, ordered by most recent first.
     * WHY: Essential for a "Communication History" screen for a customer service representative.
     */
    List<CommunicationLog> findByCustomerIdOrderBySentAtDesc(String customerId);

    /**
     * Finds all communication logs with a specific status.
     * WHY: Used by a potential retry mechanism or a monitoring batch job to find all FAILED or PENDING messages.
     */
    List<CommunicationLog> findByStatus(CommunicationStatus status);

    /**
     * Finds logs for a specific account and message type.
     * WHY: Useful for auditing to answer questions like, "Did we send the maturity notice for this account?".
     */
    List<CommunicationLog> findByAccountNumberAndTemplateType(String accountNumber, TemplateType templateType);

    /**
     * Finds the most recent communication log for a specific account and template type.
     * WHY: Can be used to prevent sending duplicate notifications. Before sending a notice, we can check
     * if the last one was sent very recently.
     */
    Optional<CommunicationLog> findTopByAccountNumberAndTemplateTypeOrderBySentAtDesc(String accountNumber, TemplateType templateType);

    /**
     * Finds all logs that are in a certain status and are older than a given timestamp.
     * WHY: Useful for a cleanup batch job, e.g., "Delete all FAILED logs that are older than 90 days".
     */
    List<CommunicationLog> findByStatusAndSentAtBefore(CommunicationStatus status, LocalDateTime timestamp);

}