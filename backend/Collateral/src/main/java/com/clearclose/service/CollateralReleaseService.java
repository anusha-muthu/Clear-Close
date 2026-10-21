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

@Service
public class CollateralReleaseService {

    private static final List<String> ACTIVE_REQUEST_STATUSES = List.of(
            "PENDING",
            "UNDER_REVIEW",
            "INFO_REQUIRED",
            "APPROVED",
            "RELEASE_IN_PROGRESS",
            "RELEASE_DELAYED");

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

        CollateralReleaseRequests request = getRequestById(requestId);
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
        request.setAssignedTo(officerId);
        if (normalizedDecision.equals("APPROVE")) {
            request.setStatus("APPROVED");
            collateral.setReleaseStatus("RELEASE_APPROVED");
        } else {
            request.setStatus("REJECTED");
            collateral.setReleaseStatus("RELEASE_REJECTED");
        }

        collateralRepository.save(collateral);
        CollateralReleaseRequests saved = releaseRepository.save(request);
        publishAudit(saved, normalizedDecision.equals("APPROVE")
                ? "COLLATERAL_APPROVED"
                : "COLLATERAL_REJECTED");
        return saved;
    }

    @Transactional
    public CollateralReleaseRequests completeRelease(Long requestId, Long officerId) {
        if (officerId == null) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Officer identity is missing from the token");
        }

        CollateralReleaseRequests request = getRequestById(requestId);
        if (!"APPROVED".equalsIgnoreCase(request.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Only an approved request can be marked as released");
        }

        Collateral collateral = getCollateralForRequest(request);
        request.setStatus("RELEASED");
        request.setReleasedAt(LocalDateTime.now());
        request.setReleaseReference("REL-CC-" + request.getRequestId());
        request.setAssignedTo(officerId);
        collateral.setReleaseStatus("RELEASED");

        collateralRepository.save(collateral);
        CollateralReleaseRequests saved = releaseRepository.save(request);
        publishAudit(saved, "COLLATERAL_RELEASED");
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

        Collateral collateral = collateralRepository.findById(request.getCollateralId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Collateral not found"));
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
        request.setAssignedTo(null);
        request.setReleasedAt(null);
        request.setReleaseReference(null);
        request.setCreatedAt(null);
        request.setUpdatedAt(null);

        collateral.setReleaseStatus("RELEASE_PENDING");
        collateralRepository.save(collateral);
        CollateralReleaseRequests saved = releaseRepository.save(request);
        publishAudit(saved, "COLLATERAL_SUBMITTED");
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

    private Collateral getCollateralForRequest(CollateralReleaseRequests request) {
        return collateralRepository.findById(request.getCollateralId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Collateral for this request was not found"));
    }

    private void publishAudit(CollateralReleaseRequests saved, String action) {
        eventPublisher.publishEvent(new AuditEvent(
                null,
                saved.getRequestedBy(),
                saved.getAssignedTo(),
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
