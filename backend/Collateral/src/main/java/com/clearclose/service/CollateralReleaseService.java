package com.clearclose.service;


import com.clearclose.entity.Collateral;
import com.clearclose.entity.CollateralReleaseRequests;
import com.clearclose.repository.CollateralRepository;
import com.clearclose.client.BorrowerClient;
import com.clearclose.config.BorrowerClientConfig;
import com.clearclose.dto.CollateralBorrowerResponse;
import com.clearclose.repository.CollateralReleaseRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CollateralReleaseService {

    private final CollateralRepository collateralRepository;
    private final CollateralReleaseRepository releaseRepository;
    private final BorrowerClient borrowerClient;
    private final CollateralService collateralService;
    
    public CollateralReleaseService(
            CollateralRepository collateralRepository,
            CollateralReleaseRepository releaseRepository,
            BorrowerClient borrowerClient,
            CollateralService collateralService) {
        this.collateralRepository = collateralRepository;
        this.releaseRepository = releaseRepository;
        this.borrowerClient = borrowerClient;
        this.collateralService = collateralService;
    }

    // Submit a release request
    @Transactional
    public CollateralReleaseRequests createRequest(
    		CollateralReleaseRequests request) {

        Collateral collateral = collateralRepository
                .findById(request.getCollateralId())
                .orElseThrow(() ->
                        new RuntimeException("Collateral not found"));

        if (!collateral.getLoanAccountId()
                .equals(request.getLoanAccountId())) {
            throw new RuntimeException(
                    "Collateral does not belong to this loan");
        }

        List<String> activeStatuses = List.of(
                "PENDING",
                "UNDER_REVIEW",
                "INFO_REQUIRED",
                "APPROVED",
                "RELEASE_IN_PROGRESS",
                "RELEASE_DELAYED"
        );

        boolean alreadyRequested =
                releaseRepository.existsByCollateralIdAndStatusIn(
                        request.getCollateralId(), activeStatuses);

        if (alreadyRequested) {
            throw new RuntimeException(
                    "An active release request already exists");
        }

        request.setStatus("PENDING");

        return releaseRepository.save(request);
    }

    // Get all release requests
    public List<CollateralReleaseRequests> getAllRequests() {
        return releaseRepository.findAll();
    }

    // Get request by ID
    public CollateralReleaseRequests getRequestById(Long id) {
        return releaseRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Release request not found: " + id));
    }

    // Get requests for a specific loan
    public List<CollateralReleaseRequests> getRequestsByLoanId(
            Long loanId) {
        return releaseRepository.findByLoanAccountId(loanId);
    }

    // Get requests submitted by a user
    public List<CollateralReleaseRequests> getRequestsByUserId(
            Long userId) {
        return releaseRepository.findByRequestedBy(userId);
    }

    // Get requests by status
    public List<CollateralReleaseRequests> getRequestsByStatus(
            String status) {
        return releaseRepository.findByStatus(status);
    }
    

	@Transactional
	public CollateralReleaseRequests submitRequest(CollateralReleaseRequests request,String username) {
		CollateralBorrowerResponse borrower = borrowerClient.getByUsername(username);
		Collateral collateral = collateralRepository.findById(request.getCollateralId())
				.orElseThrow(() ->
				new RuntimeException("Collateral not found"));
		if (!collateral.getLoanAccountId()
				.equals(request.getLoanAccountId())) {
			throw new RuntimeException("Collateral does not belong to this loan");
		}
		Object eligibleValue = collateralService
				.checkEligibility(collateral.getCollateralId())
				.get("eligible");

		if (!Boolean.TRUE.equals(eligibleValue)) {throw new RuntimeException(
				"Collateral is not eligible for release");
		}
		List<String> activeStatuses = List.of("PENDING","UNDER_REVIEW","INFO_REQUIRED",
					"APPROVED","RELEASE_IN_PROGRESS",
					"RELEASE_DELAYED");

		if (releaseRepository.existsByCollateralIdAndStatusIn(
				collateral.getCollateralId(), activeStatuses)) {
			throw new RuntimeException("An active release request already exists");
		}

		request.setRequestBy(borrower.getUserId());
		request.setStatus("PENDING");
		request.setAssignedTo(null);
		request.setReleaseAt(null);
		request.setReleaseReference(null);

		collateral.setReleaseStatus("RELEASE_PENDING");
		collateralRepository.save(collateral);
		return releaseRepository.save(request);
		}

		public List<CollateralReleaseRequests> getMyRequests(
			String username) {
			CollateralBorrowerResponse borrower = borrowerClient
					.getByUsername(username);
			return releaseRepository.findByRequestedBy(
					borrower.getUserId());
		}

	

	public List<CollateralReleaseRequests> searchRequests(Long loanId, String status) {
		return releaseRepository.findAll().stream().filter(r -> loanId == null
				|| loanId.equals(r.getLoanAccountId()))
				.filter(r -> status == null || status.isBlank()|| 
				status.equalsIgnoreCase(r.getStatus()))
				.toList();
		}
	
//	@Transactional
//	public CollateralReleaseRequests assignOfficer(
//			Long requestId, Long officerUserId) {
//	
//			CollateralReleaseRequests request = getRequestById(requestId);
//
//			
//
//			if ("RELEASED".equalsIgnoreCase(request.getStatus())
//					|| "REJECTED".equalsIgnoreCase(request.getStatus())) {
//				throw new RuntimeException(
//						"Cannot assign a completed or rejected request");
//			}
//
//			request.setAssignedTo(officer.getUserId());
//
//			if ("PENDING".equalsIgnoreCase(request.getStatus())) {
//				request.setStatus("UNDER_REVIEW");
//			}
//
//			return releaseRepository.save(request);
//	}
}