package com.clearclose.service;

import com.clearclose.dto.AuditEvent;
import com.clearclose.entity.Collateral;
import com.clearclose.entity.CollateralReleaseRequests;
import com.clearclose.repository.CollateralReleaseRepository;
import com.clearclose.repository.CollateralRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;

@Service
public class CollateralReleaseService {

    private static final List<String> ACTIVE_REQUEST_STATUSES = List.of(
            "PENDING",
            "UNDER_REVIEW",
            "INFO_REQUIRED",
            "APPROVED",
            "RELEASE_IN_PROGRESS",
            "RELEASE_DELAYED");

    @PersistenceContext
    private EntityManager entityManager;

    private final CollateralRepository collateralRepository;
    private final CollateralReleaseRepository releaseRepository;
    private final CollateralService collateralService;
    private final ApplicationEventPublisher eventPublisher;

    public CollateralReleaseService(
            CollateralRepository collateralRepository,
            CollateralReleaseRepository releaseRepository,
            CollateralService collateralService,
            ApplicationEventPublisher eventPublisher) {
        this.collateralRepository = collateralRepository;
        this.releaseRepository = releaseRepository;
        this.collateralService = collateralService;
        this.eventPublisher = eventPublisher;
    }

    public List<CollateralReleaseRequests> getAllRequests() {
        return releaseRepository.findAll();
    }

    public CollateralReleaseRequests getRequestById(Long id) {
        return releaseRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Release request not found: " + id));
    }

    public List<CollateralReleaseRequests> getRequestsByLoanId(Long loanId) {
        return releaseRepository.findByLoanAccountId(loanId);
    }

    public List<CollateralReleaseRequests> getRequestsByUserId(Long userId) {
        return releaseRepository.findByRequestedBy(userId);
    }

    public List<CollateralReleaseRequests> getRequestsByStatus(String status) {
        return releaseRepository.findByStatus(status);
    }

    @Transactional
    public CollateralReleaseRequests decideRequest(
            Long requestId,
            Long officerId,
            String decision) {
        if (officerId == null) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Officer identity is missing from the token");
        }

        CollateralReleaseRequests request = lockRequest(requestId);
        requireAssignedOfficer(request, officerId);
        if (decision == null || decision.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Decision is required: APPROVE or REJECT");
        }

        String normalizedDecision = decision.trim().toUpperCase(Locale.ROOT);
        if (!normalizedDecision.equals("APPROVE")
                && !normalizedDecision.equals("REJECT")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Decision must be APPROVE or REJECT");
        }

        String currentStatus = request.getStatus() == null
                ? ""
                : request.getStatus().trim().toUpperCase(Locale.ROOT);
        if (!List.of("PENDING", "UNDER_REVIEW", "INFO_REQUIRED")
                .contains(currentStatus)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "This request can no longer be decided");
        }

        Collateral collateral = getCollateralForRequest(request);
        if (normalizedDecision.equals("APPROVE")) {
            request.setStatus("APPROVED");
            collateral.setReleaseStatus("RELEASE_PENDING");
        } else {
            request.setStatus("REJECTED");
            collateral.setReleaseStatus("ELIGIBLE");
        }

        collateralRepository.save(collateral);
        request.setUpdatedAt(LocalDateTime.now());
        CollateralReleaseRequests saved = releaseRepository.save(request);
        publishAudit(saved, normalizedDecision.equals("APPROVE")
                ? "COLLATERAL_APPROVED"
                : "COLLATERAL_REJECTED", officerId);
        return saved;
    }

    @Transactional
    public CollateralReleaseRequests completeRelease(Long requestId, Long officerId) {
        if (officerId == null) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Officer identity is missing from the token");
        }

        CollateralReleaseRequests request = lockRequest(requestId);
        requireAssignedOfficer(request, officerId);
        if (!List.of("APPROVED", "RELEASE_IN_PROGRESS", "RELEASE_DELAYED").contains(request.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Only an approved or processing request can be marked as released");
        }

        Collateral collateral = getCollateralForRequest(request);
        request.setStatus("RELEASED");
        request.setReleasedAt(LocalDateTime.now());
        request.setReleaseReference("REL-CC-" + request.getRequestId());
        collateral.setReleaseStatus("RELEASED");

        collateralRepository.save(collateral);
        request.setUpdatedAt(LocalDateTime.now());
        CollateralReleaseRequests saved = releaseRepository.save(request);
        publishAudit(saved, "COLLATERAL_RELEASED", officerId);
        return saved;
    }

    @Transactional
    public CollateralReleaseRequests submitRequest(
            CollateralReleaseRequests request,
            Long borrowerId,
            String bearerToken) {
        if (borrowerId == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Borrower identity is missing from the token");
        }
        if (request.getCollateralId() == null || request.getLoanAccountId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Collateral ID and loan account ID are required");
        }

        Collateral collateral = lockCollateral(request.getCollateralId());
        if (!collateral.getLoanAccountId().equals(request.getLoanAccountId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Collateral does not belong to this loan");
        }

        collateralService.assertLoanOwnedByBorrower(
                collateral.getLoanAccountId(), borrowerId, bearerToken);
        boolean canSubmit = Boolean.TRUE.equals(
                collateralService.checkEligibility(
                        collateral.getCollateralId(), bearerToken).get("canSubmitRequest"));
        if (!canSubmit) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Collateral is not currently eligible for a release request");
        }

        if (releaseRepository.existsByCollateralIdAndStatusIn(
                collateral.getCollateralId(), ACTIVE_REQUEST_STATUSES)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "An active release request already exists");
        }

        request.setRequestId(null);
        request.setCollateralId(collateral.getCollateralId());
        request.setLoanAccountId(collateral.getLoanAccountId());
        request.setRequestedBy(borrowerId);
        request.setStatus("PENDING");
        request.setAssignedTo(chooseCollateralOfficer(borrowerId));
        request.setReleasedAt(null);
        request.setReleaseReference(null);
        request.setCreatedAt(null);
        request.setUpdatedAt(null);

        collateral.setReleaseStatus("RELEASE_PENDING");
        collateralRepository.save(collateral);
        request.setUpdatedAt(LocalDateTime.now());
        CollateralReleaseRequests saved = releaseRepository.save(request);
        publishAudit(saved, "COLLATERAL_SUBMITTED", null);
        return saved;
    }

    public List<CollateralReleaseRequests> getMyRequests(Long borrowerId) {
        return releaseRepository.findByRequestedBy(borrowerId);
    }

    public List<CollateralReleaseRequests> searchRequests(Long loanId, String status) {
        return releaseRepository.findAll().stream()
                .filter(request -> loanId == null || loanId.equals(request.getLoanAccountId()))
                .filter(request -> status == null || status.isBlank()
                        || status.equalsIgnoreCase(request.getStatus()))
                .toList();
    }

    private record Officer(Long id, Long borrowerId, String role) {}

    private static Long numericId(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private Officer activeOfficer(Long officerId) {
        if (officerId == null || officerId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valid officer ID required");
        }
        List<?> rows = entityManager.createNativeQuery("""
            SELECT OFFICER_ID, USER_ID, ROLE FROM OFFICER
            WHERE OFFICER_ID = :id AND ACCOUNT_STATUS = 'ACTIVE'
              AND ROLE IN ('COLLATERAL_OFFICER', 'ADMIN')
            """).setParameter("id", officerId).getResultList();
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.FORBIDDEN,
            "An active collateral officer or administrator is required");
        Object[] row = (Object[]) rows.get(0);
        return new Officer(numericId(row[0]), numericId(row[1]), (String) row[2]);
    }

    private Long chooseCollateralOfficer(Long borrowerId) {
        List<?> rows = entityManager.createNativeQuery("""
            SELECT o.OFFICER_ID FROM OFFICER o
            WHERE o.ROLE = 'COLLATERAL_OFFICER' AND o.ACCOUNT_STATUS = 'ACTIVE'
              AND (o.USER_ID IS NULL OR o.USER_ID <> :borrowerId)
            ORDER BY (
                SELECT COUNT(*) FROM COLLATERAL_RELEASE_REQUESTS r
                WHERE r.ASSIGNED_TO = o.OFFICER_ID
                  AND r.STATUS IN ('PENDING', 'UNDER_REVIEW', 'INFO_REQUIRED',
                    'APPROVED', 'RELEASE_IN_PROGRESS', 'RELEASE_DELAYED')
            ), o.OFFICER_ID
            """).setParameter("borrowerId", borrowerId).setMaxResults(1).getResultList();
        return rows.isEmpty() ? null : numericId(rows.get(0));
    }

    private CollateralReleaseRequests lockRequest(Long requestId) {
        if (requestId == null || requestId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valid request ID required");
        }
        CollateralReleaseRequests request = entityManager.find(
            CollateralReleaseRequests.class, requestId, LockModeType.PESSIMISTIC_WRITE);
        if (request == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Release request not found");
        entityManager.refresh(request, LockModeType.PESSIMISTIC_WRITE);
        return request;
    }

    private Collateral lockCollateral(Long collateralId) {
        Collateral collateral = entityManager.find(Collateral.class, collateralId, LockModeType.PESSIMISTIC_WRITE);
        if (collateral == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Collateral not found");
        entityManager.refresh(collateral, LockModeType.PESSIMISTIC_WRITE);
        return collateral;
    }

    private void requireAssignedOfficer(CollateralReleaseRequests request, Long officerId) {
        Officer officer = activeOfficer(officerId);
        if (!Objects.equals(officerId, request.getAssignedTo())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the assigned officer can act on this request");
        }
        if (Objects.equals(officer.borrowerId(), request.getRequestedBy())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Self-review is not permitted");
        }
    }

    @Transactional(readOnly = true)
    public List<CollateralReleaseRequests> getAssignedRequests(Long officerId) {
        Officer officer = activeOfficer(officerId);
        List<?> rows = entityManager.createNativeQuery("""
            SELECT r.* FROM COLLATERAL_RELEASE_REQUESTS r
            WHERE r.ASSIGNED_TO = :officerId
            ORDER BY r.CREATED_AT DESC, r.REQUEST_ID DESC
            """, CollateralReleaseRequests.class)
            .setParameter("officerId", officerId).getResultList();
        return rows.stream().map(CollateralReleaseRequests.class::cast)
            .filter(r -> officer.borrowerId() == null || !officer.borrowerId().equals(r.getRequestedBy()))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<CollateralReleaseRequests> searchVisibleRequests(
            Long loanId, String status, Long actorId, String accountType) {
        List<CollateralReleaseRequests> visible;
        if ("BORROWER".equals(accountType)) {
            visible = getMyRequests(actorId);
        } else if ("OFFICER".equals(accountType)) {
            Officer officer = activeOfficer(actorId);
            visible = "ADMIN".equals(officer.role()) ? releaseRepository.findAll() : getAssignedRequests(actorId);
        } else {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid account type");
        }
        return visible.stream()
            .filter(r -> loanId == null || loanId.equals(r.getLoanAccountId()))
            .filter(r -> status == null || status.isBlank() || status.equalsIgnoreCase(r.getStatus()))
            .toList();
    }

    @Transactional(readOnly = true)
    public CollateralReleaseRequests getVisibleRequest(Long requestId, Long actorId, String accountType) {
        CollateralReleaseRequests request = getRequestById(requestId);
        if ("BORROWER".equals(accountType) && actorId.equals(request.getRequestedBy())) return request;
        if ("OFFICER".equals(accountType)) {
            Officer officer = activeOfficer(actorId);
            if ("ADMIN".equals(officer.role())) return request;
            requireAssignedOfficer(request, actorId);
            return request;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This release request is not accessible");
    }

    // Existing unassigned request: an administrator may omit officerId to choose
    // an eligible collateral officer automatically, or supply an explicit target.
    @Transactional
    public CollateralReleaseRequests assignRequest(Long requestId, Long targetId, Long adminId) {
        if (!"ADMIN".equals(activeOfficer(adminId).role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator access required");
        }
        CollateralReleaseRequests request = lockRequest(requestId);
        if (!ACTIVE_REQUEST_STATUSES.contains(request.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only active requests can be assigned");
        }
        Long selectedId = targetId == null ? chooseCollateralOfficer(request.getRequestedBy()) : targetId;
        if (selectedId == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "No eligible collateral officer is available");
        Officer target = activeOfficer(selectedId);
        if (Objects.equals(target.borrowerId(), request.getRequestedBy())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Self-review is not permitted");
        }
        request.setAssignedTo(selectedId);
        request.setUpdatedAt(LocalDateTime.now());
        CollateralReleaseRequests saved = releaseRepository.save(request);
        publishAudit(saved, "COLLATERAL_REQUEST_ASSIGNED", adminId);
        return saved;
    }

    private Collateral getCollateralForRequest(CollateralReleaseRequests request) {
        return lockCollateral(request.getCollateralId());
    }

    private void publishAudit(CollateralReleaseRequests saved, String action, Long actingOfficerId) {
        eventPublisher.publishEvent(new AuditEvent(
                null,
                saved.getRequestedBy(),
                actingOfficerId,
                saved.getRequestId(),
                "COLLATERAL_RELEASE_REQUEST",
                action,
                LocalDateTime.now(),
                null,
                null,
                null,
                null,
                null,
                null,
                saved.getRequestId()));
    }
}
