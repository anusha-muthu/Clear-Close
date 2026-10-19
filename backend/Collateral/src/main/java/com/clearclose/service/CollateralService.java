package com.clearclose.service;

import com.clearclose.entity.Collateral;
import com.clearclose.entity.CollateralReleaseRequests;
import com.clearclose.exception.ResourceNotFoundException;
import com.clearclose.repository.CollateralReleaseRepository;
import com.clearclose.repository.CollateralRepository;
import com.clearclose.client.LoanAccountClient;
import com.clearclose.dto.CollateralLoanResponse;
import jakarta.transaction.Transactional;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
public class CollateralService {

    private final CollateralRepository collateralRepository;
    private final CollateralReleaseRepository collateralReleaseRepository;
    private final LoanAccountClient loanClient;

    public List<String> getCollateralTypes() {
    	return List.of("PROPERTY","GOLD","VEHICLE","FIXED_DEPOSIT","OTHER");
    }

    public CollateralService(CollateralRepository collateralRepository,LoanAccountClient loanClient,CollateralReleaseRepository collateralReleaseRepository) {
        this.collateralRepository = collateralRepository;
        this.loanClient=loanClient;
        this.collateralReleaseRepository=collateralReleaseRepository;
    }

    public Collateral createCollateral(Collateral collateral) {
        collateral.setReleaseStatus("NOT_ELIGIBLE");
        return collateralRepository.save(collateral);
    }

    public List<Collateral> getAllCollateral() {
        return collateralRepository.findAll();
    }

    public Collateral getCollateralById(Long id) {
        return collateralRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Collateral not found: " + id));
    }

    public List<Collateral> getCollateralByLoanId(Long loanId) {
    	List<Collateral> collaterals = collateralRepository.findByLoanAccountId(loanId);
        
        if (collaterals.isEmpty()) {
            throw new ResourceNotFoundException("Collateral not found for Loan ID: " + loanId);
        }
        
        return collaterals;
    }

    public Collateral updateCollateral(Long id, Collateral updated) {
        Collateral existing = getCollateralById(id);

        existing.setCollateralType(updated.getCollateralType());
        existing.setDescription(updated.getDescription());
        existing.setCustodian(updated.getCustodian());
        existing.setReleaseStatus(updated.getReleaseStatus());

        return collateralRepository.save(existing);
    }
    public Map<String, Object> checkEligibility(Long collateralId) {

        Collateral collateral = collateralRepository.findById(collateralId)
                .orElseThrow(() ->
                        new RuntimeException("Collateral not found"));

        CollateralLoanResponse loan =
                loanClient.getByLoanId(collateral.getLoanAccountId());

        if (loan == null) {
            throw new RuntimeException("Loan service returned no loan data");
        }

        boolean loanClosed =
                "CLOSED".equalsIgnoreCase(loan.getLoanStatus());

        BigDecimal outstanding = loan.getOutstandingAmount();

        boolean fullySettled = outstanding != null
                && outstanding.compareTo(BigDecimal.ZERO) <= 0;

        boolean alreadyReleased =
                "RELEASED".equalsIgnoreCase(collateral.getReleaseStatus());

        boolean eligible = loanClosed && fullySettled && !alreadyReleased;

        List<CollateralReleaseRequests> existingRequests =
                collateralReleaseRepository.findByCollateralIdAndStatusIn(
                        collateralId,
                        List.of("PENDING", "UNDER_REVIEW"));

        String requestStatus = existingRequests.stream()
                .anyMatch(request ->
                        "UNDER_REVIEW".equalsIgnoreCase(request.getStatus()))
                ? "UNDER_REVIEW"
                : existingRequests.isEmpty() ? "NONE" : "PENDING";

        boolean canSubmitRequest =
                eligible && existingRequests.isEmpty();

        String message;
        if (alreadyReleased) {
            message = "Collateral has already been released";
        } else if ("UNDER_REVIEW".equals(requestStatus)) {
            message = "Release request is under review";
        } else if ("PENDING".equals(requestStatus)) {
            message = "Release request already submitted and pending review";
        } else if (!loanClosed) {
            message = "Loan must be closed before collateral release";
        } else if (!fullySettled) {
            message = "Loan must be fully settled before collateral release";
        } else {
            message = "Collateral is eligible for a release request";
        }

        return Map.of(
                "collateralId", collateralId,
                "loanAccountId", collateral.getLoanAccountId(),
                "eligible", eligible,
                "canSubmitRequest", canSubmitRequest,
//                "requestStatus", requestStatus,
                "message", message
        );
    }

}