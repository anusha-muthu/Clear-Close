package com.ofss.repository;

import com.ofss.entity.Grievances;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface GrievancesRepository
        extends JpaRepository<Grievances, Long> {

    List<Grievances> findByBorrowerId(Long borrowerId);

    List<Grievances> findByAssignedTo(Long officerId);
    
    List<Grievances> findByStatusIgnoreCase(String status);
    List<Grievances> findByLoanAccountId(Long loanAccountId);
    List<Grievances> findByStatusNotInAndCreatedAtBefore(List<String> statuses,LocalDateTime cutoff);

}
