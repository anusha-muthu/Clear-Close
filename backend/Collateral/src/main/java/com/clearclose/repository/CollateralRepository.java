package com.clearclose.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.clearclose.entity.Collateral;
@Repository
public interface CollateralRepository extends JpaRepository <Collateral,Long>{
	List<Collateral> findByLoanAccountId(Long loanAccountId);
	List<Collateral> findByReleaseStatus(String releaseStatus);
	
	
}
