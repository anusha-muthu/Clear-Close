package com.clearclose.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.clearclose.entity.LoanAccount;

@Repository
public interface LoanAccountRepository extends JpaRepository<LoanAccount, Long>{
	Optional<LoanAccount> findByLoanId(Long loanId);
}
