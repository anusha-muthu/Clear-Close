package com.clearclose.service;

import com.clearclose.entity.Collateral;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.clearclose.entity.CollateralReleaseRequests;
import com.clearclose.exception.ResourceNotFoundException;
import com.clearclose.repository.CollateralReleaseRepository;
import com.clearclose.repository.CollateralRepository;
import com.clearclose.client.LoanAccountClient;
import com.clearclose.client.NocClient;
import com.clearclose.dto.CollateralLoanResponse;
import jakarta.transaction.Transactional;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
public class CollateralService {

    private static final List<String> ACTIVE_REQUEST_STATUSES = List.of(
            "PENDING",
            "UNDER_REVIEW",
            "INFO_REQUIRED",
            "APPROVED",
            "RELEASE_IN_PROGRESS",
            "RELEASE_DELAYED"
    );

    private final CollateralRepository collateralRepository;
    private final CollateralReleaseRepository collateralReleaseRepository;
    private final LoanAccountClient loanClient;
    private final NocClient nocClient;

    public List<String> getCollateralTypes() {
    	return List.of("PROPERTY","GOLD","VEHICLE","FIXED_DEPOSIT","OTHER");
    }

    public CollateralService(CollateralRepository collateralRepository,LoanAccountClient loanClient,CollateralReleaseRepository collateralReleaseRepository, NocClient nocClient) {
        this.collateralRepository = collateralRepository;
        this.loanClient=loanClient;
        this.nocClient = nocClient;
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
        // Release status is controlled by the release-request workflow.

        return collateralRepository.save(existing);
    }
    public Map<String, Object> checkEligibility(Long collateralId, String bearerToken) {

        Collateral collateral = collateralRepository.findById(collateralId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Collateral not found: " + collateralId));

        CollateralLoanResponse loan =
                loanClient.getByLoanId(collateral.getLoanAccountId(), bearerToken);

        if (loan == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Loan Account service returned no loan data");
        }

        boolean loanClosed =
        	    "CLOSED".equalsIgnoreCase(loan.getLoanStatus());

        	BigDecimal outstanding = loan.getOutstandingAmount();

        	// Match the zero-balance requirement in your NocService.
        	boolean fullySettled =
        	    outstanding != null
        	        && outstanding.compareTo(BigDecimal.ZERO) == 0;

        	boolean alreadyReleased =
        	    "RELEASED".equalsIgnoreCase(collateral.getReleaseStatus());

        	boolean nocIssued = nocClient.hasAvailableNoc(
        		    collateral.getLoanAccountId(),
        		    bearerToken
        		);

        	boolean eligible =
        	    loanClosed && fullySettled && nocIssued && !alreadyReleased;

        	List<CollateralReleaseRequests> existingRequests =
        	    collateralReleaseRepository.findByCollateralIdAndStatusIn(
        	        collateralId,
        	        ACTIVE_REQUEST_STATUSES
        	    );

        	String requestStatus = existingRequests.isEmpty()
        	    ? "NONE"
        	    : existingRequests.get(0).getStatus();

        	boolean canSubmitRequest =
        	    eligible && existingRequests.isEmpty();

        	String message;

        	if (alreadyReleased) {
        	    message = "Collateral has already been released";
        	} else if (!loanClosed) {
        	    message = "Loan must be closed before collateral release";
        	} else if (!fullySettled) {
        	    message = "Loan must be fully settled before collateral release";
        	} else if (!nocIssued) {
        	    message = "NOC is still pending. Release can be requested after issuance.";
        	} else if (!existingRequests.isEmpty()) {
        	    message = "A release request is already active (" + requestStatus + ")";
        	} else {
        	    message = "Collateral is eligible for a release request";
        	}

        	return Map.of(
        	    "collateralId", collateralId,
        	    "loanAccountId", collateral.getLoanAccountId(),
        	    "nocIssued", nocIssued,
        	    "eligible", eligible,
        	    "canSubmitRequest", canSubmitRequest,
        	    "requestStatus", requestStatus,
        	    "message", message
        	);
    }

    public void assertLoanOwnedByBorrower(
            Long loanId, Long borrowerId, String bearerToken) {
        CollateralLoanResponse loan = loanClient.getByLoanId(loanId, bearerToken);

        if (loan == null
                || loan.getBorrowerId() == null
                || !loan.getBorrowerId().equals(borrowerId)) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "This loan does not belong to the authenticated borrower"
            );
        }
    }

}

