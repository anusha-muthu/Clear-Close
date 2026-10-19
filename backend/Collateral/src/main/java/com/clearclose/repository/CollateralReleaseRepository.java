package com.clearclose.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.clearclose.entity.CollateralReleaseRequests;
@Repository
public interface CollateralReleaseRepository extends JpaRepository <CollateralReleaseRequests,Long>{
	List<CollateralReleaseRequests> findByLoanAccountId(Long loanAccountId);
	List<CollateralReleaseRequests> findByStatus(String Status);
	List<CollateralReleaseRequests> findByRequestedBy(Long requestedBy);
	List<CollateralReleaseRequests> findByAssignedTo(Long assignedTo);
	Optional<CollateralReleaseRepository> findByCollateralId(Long collateralId);
	boolean existsByCollateralIdAndStatusIn(Long collateralId,List<String> statuses);
	List<CollateralReleaseRequests> findByCollateralIdAndStatusIn(
	        Long collateralId, List<String> statuses);

}
