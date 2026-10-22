package com.ofss.service;

import com.ofss.dto.AuditEvent;
import com.ofss.entity.Grievances;
import com.ofss.entity.GrievancesHistory;
import com.ofss.repository.GrievancesRepository;
import com.ofss.repository.GrievancesHistoryRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class GrievanceService {
    private final GrievancesRepository grievanceRepository;
    private final GrievancesHistoryRepository historyRepository;
    private final ApplicationEventPublisher eventPublisher;

    @PersistenceContext
    private EntityManager entityManager;

    public GrievanceService(
            GrievancesRepository grievanceRepository,
            GrievancesHistoryRepository historyRepository,
            ApplicationEventPublisher eventPublisher) {
        this.grievanceRepository = grievanceRepository;
        this.historyRepository = historyRepository;
        this.eventPublisher = eventPublisher;
    }

    private record Officer(Long id, Long borrowerId, String role) {}

    private static ResponseStatusException error(HttpStatus status, String reason) {
        return new ResponseStatusException(status, reason);
    }

    private static Long id(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private static void requireActor(Long actorId, String actorType, String expectedType) {
        if (actorId == null || actorId <= 0 || !expectedType.equals(actorType)) {
            throw error(HttpStatus.FORBIDDEN, "A valid " + expectedType.toLowerCase() + " account is required");
        }
    }

    private Officer activeOfficer(Long officerId) {
        if (officerId == null || officerId <= 0) {
            throw error(HttpStatus.BAD_REQUEST, "A valid officer ID is required");
        }
        List<?> rows = entityManager.createNativeQuery("""
            SELECT OFFICER_ID, USER_ID, ROLE FROM OFFICER
            WHERE OFFICER_ID = :id AND ACCOUNT_STATUS = 'ACTIVE'
            """).setParameter("id", officerId).getResultList();
        if (rows.isEmpty()) throw error(HttpStatus.FORBIDDEN, "Officer account is not active");
        Object[] row = (Object[]) rows.get(0);
        String role = (String) row[2];
        if (!GrievancePolicy.OFFICER_ROLES.contains(role)) {
            throw error(HttpStatus.FORBIDDEN, "Officer role is not permitted");
        }
        return new Officer(id(row[0]), id(row[1]), role);
    }

    private Officer requireAdmin(Long actorId, String actorType) {
        requireActor(actorId, actorType, "OFFICER");
        Officer actor = activeOfficer(actorId);
        if (!"ADMIN".equals(actor.role())) {
            throw error(HttpStatus.FORBIDDEN, "Only an administrator can assign grievances");
        }
        return actor;
    }

    private Grievances lockedGrievance(Long grievanceId) {
        if (grievanceId == null || grievanceId <= 0) {
            throw error(HttpStatus.BAD_REQUEST, "A valid grievance ID is required");
        }
        Grievances grievance = entityManager.find(
            Grievances.class, grievanceId, LockModeType.PESSIMISTIC_WRITE);
        if (grievance == null) throw error(HttpStatus.NOT_FOUND, "Grievance not found");
        // A controller read may have populated the persistence context already.
        // Refresh while locked so authorization uses the current assignment.
        entityManager.refresh(grievance, LockModeType.PESSIMISTIC_WRITE);
        return grievance;
    }

    private Long chooseOfficer(String category, Long borrowerId) {
        List<?> rows = entityManager.createNativeQuery("""
            SELECT o.OFFICER_ID
            FROM OFFICER o
            WHERE o.ROLE = :role AND o.ACCOUNT_STATUS = 'ACTIVE'
              AND (o.USER_ID IS NULL OR o.USER_ID <> :borrowerId)
            ORDER BY (
                SELECT COUNT(*) FROM GRIEVANCES g
                WHERE g.ASSIGNED_TO = o.OFFICER_ID
                  AND g.STATUS NOT IN ('RESOLVED', 'REJECTED', 'CLOSED')
            ), o.OFFICER_ID
            """)
            .setParameter("role", GrievancePolicy.roleForCategory(category))
            .setParameter("borrowerId", borrowerId)
            .setMaxResults(1)
            .getResultList();
        return rows.isEmpty() ? null : id(rows.get(0));
    }

    // Retained for existing internal callers. HTTP callers must use the JWT-derived
    // overload below, as in your supplied GrievanceController.
    @Transactional
    public Grievances submitGrievance(Grievances grievance) {
        return submitGrievance(grievance, grievance.getBorrowerId(), "BORROWER");
    }

    @Transactional
    public Grievances submitGrievance(Grievances input, Long actorId, String actorType) {
        requireActor(actorId, actorType, "BORROWER");
        if (input == null || input.getLoanAccountId() == null || input.getLoanAccountId() <= 0) {
            throw error(HttpStatus.BAD_REQUEST, "A valid loan account ID is required");
        }
        String category;
        try {
            category = GrievancePolicy.newCategory(input.getCategory());
        } catch (IllegalArgumentException ex) {
            throw error(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
        String description = input.getDescription() == null ? "" : input.getDescription().trim();
        if (description.isEmpty() || description.length() > 1000) {
            throw error(HttpStatus.BAD_REQUEST, "Description must contain 1 to 1000 characters including the subject");
        }

        // Lock the parent loan so concurrent submissions through this method cannot
        // both pass the existing one-grievance-per-loan check.
        List<?> owners = entityManager.createNativeQuery("""
            SELECT BORROWER_ID FROM LOAN_ACCOUNT WHERE LOAN_ID = :loanId FOR UPDATE
            """).setParameter("loanId", input.getLoanAccountId()).getResultList();
        if (owners.isEmpty()) throw error(HttpStatus.NOT_FOUND, "Loan account not found");
        if (!actorId.equals(id(owners.get(0)))) {
            throw error(HttpStatus.FORBIDDEN, "You can submit grievances only for your own loan");
        }
        Number activeBorrowers = (Number) entityManager.createNativeQuery("""
            SELECT COUNT(*) FROM BORROWERS WHERE USER_ID = :id AND ACCOUNT_STATUS = 'ACTIVE'
            """).setParameter("id", actorId).getSingleResult();
        if (activeBorrowers.longValue() != 1) throw error(HttpStatus.FORBIDDEN, "Borrower account is not active");
        if (grievanceRepository.existsByLoanAccountId(input.getLoanAccountId())) {
            throw error(HttpStatus.CONFLICT, "A grievance already exists for this loan.");
        }

        // Build a fresh entity: ignore any ID, assignment, status, timestamps or
        // resolution supplied in the borrower request body.
        Grievances grievance = new Grievances();
        grievance.setBorrowerId(actorId);
        grievance.setLoanAccountId(input.getLoanAccountId());
        grievance.setCategory(category);
        grievance.setDescription(description);
        grievance.setStatus("OPEN");
        grievance.setResolutionRemarks(null);
        grievance.setAssignedTo(chooseOfficer(category, actorId));
        grievance.setUpdatedAt(LocalDateTime.now());
        Grievances saved = grievanceRepository.save(grievance);

        saveHistory(saved.getGrievanceId(), null, "OPEN", "Grievance submitted by borrower", actorId, "BORROWER");
        publishAudit(saved, "GRIEVANCE_SUBMITTED", null);
        if (saved.getAssignedTo() != null) {
            saveHistory(saved.getGrievanceId(), "OPEN", "OPEN",
                "Auto-assigned by category to officer " + saved.getAssignedTo(), null, "SYSTEM");
            publishAudit(saved, "GRIEVANCE_AUTO_ASSIGNED", null);
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Grievances> getAllGrievances() { return grievanceRepository.findAll(); }

    @Transactional(readOnly = true)
    public Grievances getGrievance(Long grievanceId) {
        return grievanceRepository.findById(grievanceId).orElseThrow(
            () -> error(HttpStatus.NOT_FOUND, "Grievance not found"));
    }

    @Transactional(readOnly = true)
    public List<Grievances> getByBorrower(Long borrowerId) {
        return grievanceRepository.findByBorrowerId(borrowerId);
    }

    @Transactional(readOnly = true)
    public List<Grievances> getByOfficer(Long officerId) {
        Officer officer = activeOfficer(officerId);
        return grievanceRepository.findByAssignedTo(officerId).stream()
            .filter(g -> officer.borrowerId() == null || !officer.borrowerId().equals(g.getBorrowerId()))
            .toList();
    }

    @Transactional
    public Grievances assignGrievance(Long grievanceId, Long officerId, Long actionBy) {
        return assignGrievance(grievanceId, officerId, actionBy, "OFFICER");
    }

    @Transactional
    public Grievances assignGrievance(
            Long grievanceId,
            Long officerId,
            Long actionBy,
            String actorType) {

        requireAdmin(actionBy, actorType);

        Grievances grievance = lockedGrievance(grievanceId);
        Officer target = activeOfficer(officerId);

        if (Objects.equals(
                target.borrowerId(),
                grievance.getBorrowerId())) {

            throw error(
                HttpStatus.FORBIDDEN,
                "An officer cannot review their own borrower grievance"
            );
        }

        String requiredRole =
            "ESCALATED".equals(grievance.getStatus())
                ? "SENIOR_GRIEVANCE_OFFICER"
                : GrievancePolicy.roleForCategory(
                    grievance.getCategory()
                );

        if (!requiredRole.equals(target.role())
                && !"ADMIN".equals(target.role())) {

            throw error(
                HttpStatus.BAD_REQUEST,
                "This grievance requires " + requiredRole + " or ADMIN"
            );
        }

        grievance.setAssignedTo(officerId);
        grievance.setUpdatedAt(LocalDateTime.now());

        Grievances saved = grievanceRepository.save(grievance);

        saveHistory(
            grievanceId,
            saved.getStatus(),
            saved.getStatus(),
            "Assigned to officer " + officerId,
            actionBy,
            "OFFICER"
        );

        publishAudit(saved, "GRIEVANCE_ASSIGNED", actionBy);

        return saved;
    }
    
    private boolean assignSeniorForEscalation(
            Grievances grievance) {

        List<?> officers = entityManager.createNativeQuery("""
            SELECT o.OFFICER_ID
            FROM OFFICER o
            WHERE o.ROLE = 'SENIOR_GRIEVANCE_OFFICER'
              AND o.ACCOUNT_STATUS = 'ACTIVE'
              AND (
                  o.USER_ID IS NULL
                  OR o.USER_ID <> :borrowerId
              )
            ORDER BY (
                SELECT COUNT(*)
                FROM GRIEVANCES g
                WHERE g.ASSIGNED_TO = o.OFFICER_ID
                  AND g.STATUS NOT IN (
                      'RESOLVED', 'REJECTED', 'CLOSED'
                  )
            ), o.OFFICER_ID
            """)
            .setParameter(
                "borrowerId",
                grievance.getBorrowerId()
            )
            .setMaxResults(1)
            .getResultList();

        if (officers.isEmpty()) {
            // Keep the current assignment until an administrator
            // assigns an available senior officer.
            return false;
        }

        Long seniorId =
            ((Number) officers.get(0)).longValue();

        grievance.setAssignedTo(seniorId);
        return true;
    }

    @Transactional
    public Grievances updateStatus(Long grievanceId, String newStatus, String remarks, Long actionBy) {
        return updateStatus(grievanceId, newStatus, remarks, actionBy, "OFFICER");
    }

    @Transactional
    public Grievances updateStatus(Long grievanceId, String newStatus, String remarks, Long actionBy, String actorType) {
        requireActor(actionBy, actorType, "OFFICER");
        Officer actor = activeOfficer(actionBy);
        Grievances grievance = lockedGrievance(grievanceId);
        if (!Objects.equals(actionBy, grievance.getAssignedTo())) {
            throw error(HttpStatus.FORBIDDEN, "Only the assigned officer can update this grievance");
        }
        if (Objects.equals(actor.borrowerId(), grievance.getBorrowerId())) {
            throw error(HttpStatus.FORBIDDEN, "An officer cannot review their own borrower grievance");
        }
        String normalizedStatus = GrievancePolicy.normalize(newStatus);
        if (!GrievancePolicy.STATUSES.contains(normalizedStatus)) {
            throw error(HttpStatus.BAD_REQUEST, "Invalid grievance status");
        }
        String oldStatus = grievance.getStatus();
        if (!GrievancePolicy.canTransition(oldStatus, normalizedStatus)) {
            throw error(HttpStatus.CONFLICT, "Status transition is not allowed; refresh the complaint");
        }
        String actionRemarks = remarks == null ? "" : remarks.trim();
        if (actionRemarks.isEmpty() || actionRemarks.length() > 1000) {
            throw error(HttpStatus.BAD_REQUEST, "Action remarks must contain 1 to 1000 characters");
        }
        grievance.setStatus(normalizedStatus);
        if ("ESCALATED".equals(normalizedStatus)) {
            assignSeniorForEscalation(grievance);
        }
        grievance.setResolutionRemarks(actionRemarks);
        grievance.setUpdatedAt(LocalDateTime.now());
        Grievances saved = grievanceRepository.save(grievance);
        saveHistory(grievanceId, oldStatus, normalizedStatus, actionRemarks, actionBy, "OFFICER");
        publishAudit(saved, "GRIEVANCE_STATUS_CHANGED_TO_" + normalizedStatus, actionBy);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<GrievancesHistory> getHistory(Long grievanceId) {
        getGrievance(grievanceId);
        return historyRepository.findByGrievanceIdOrderByActionAtAsc(grievanceId);
    }

    @Transactional(readOnly = true)
    public List<Grievances> getByStatus(String status) {
        return grievanceRepository.findByStatusIgnoreCase(status);
    }

    @Transactional(readOnly = true)
    public List<Grievances> getByLoanId(Long loanId) {
        return grievanceRepository.findByLoanAccountId(loanId);
    }

    @Transactional(readOnly = true)
    public List<Grievances> getOverdueGrievances() {
        return grievanceRepository.findByStatusNotInAndCreatedAtBefore(
            List.of("RESOLVED", "REJECTED", "CLOSED", "ESCALATED"), LocalDateTime.now().minusDays(5));
    }

    // Called through a separate Spring bean so the transaction interceptor runs.
    // Uses the same local timestamp basis as existing CREATED_AT values.
    @Transactional
    public int escalateOverdueGrievances() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minusDays(5);
        List<?> candidates = entityManager.createNativeQuery("""
            SELECT GRIEVANCE_ID FROM GRIEVANCES
            WHERE CREATED_AT < :cutoff
              AND STATUS IN ('OPEN', 'IN_PROGRESS', 'MORE_INFO_REQUIRED')
            ORDER BY CREATED_AT, GRIEVANCE_ID
            """).setParameter("cutoff", Timestamp.valueOf(cutoff))
            .setMaxResults(100)
            .getResultList();
        log.info(
        	    "Escalation check: now={}, cutoff={}, candidates={}",
        	    now, cutoff, candidates
        	);
        int changed = 0;
        for (Object candidate : candidates) {
            // Lock and refresh: another instance or officer may have handled it
            // after the initial query. Recheck before adding any history/event.
            Grievances grievance = lockedGrievance(id(candidate));
            if (!GrievancePolicy.isOverdue(
                    grievance.getStatus(), grievance.getCreatedAt(), now)) continue;
            String oldStatus = grievance.getStatus();
            grievance.setStatus("ESCALATED");
            assignSeniorForEscalation(grievance);
            grievance.setUpdatedAt(now);
            // Keep assignee and officer remarks. The system action goes in history.
            Grievances saved = grievanceRepository.save(grievance);
            saveHistory(saved.getGrievanceId(), oldStatus, "ESCALATED",
                "Automatically escalated: unresolved for more than five days since submission.",
                null, "SYSTEM");
            publishAudit(saved, "GRIEVANCE_AUTO_ESCALATED", null);
            changed++;
        }
        return changed;
    }

    private void publishAudit(Grievances saved, String action, Long actingOfficerId) {
        // OFFICER_ID is the acting officer, not automatically the assignee.
        eventPublisher.publishEvent(new AuditEvent(
            null, saved.getBorrowerId(), actingOfficerId, saved.getGrievanceId(),
            "GRIEVANCE", action, LocalDateTime.now(),
            null, null, null, null, null, saved.getGrievanceId(), null));
    }

    private void saveHistory(Long grievanceId, String oldStatus, String newStatus,
            String remarks, Long actionBy, String actorType) {
        GrievancesHistory history = new GrievancesHistory();
        history.setGrievanceId(grievanceId);
        history.setOldStatus(oldStatus);
        history.setNewStatus(newStatus);
        history.setActionRemarks(remarks);
        history.setActionBy(actionBy);
        history.setActorType(actorType);
        historyRepository.save(history);
    }
    
    private static final Logger log =
    	    LoggerFactory.getLogger(GrievanceService.class);
}
