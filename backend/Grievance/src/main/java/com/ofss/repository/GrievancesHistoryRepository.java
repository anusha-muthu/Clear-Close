package com.ofss.repository;

import com.ofss.entity.GrievancesHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface GrievancesHistoryRepository
        extends JpaRepository<GrievancesHistory, Long> {

    List<GrievancesHistory>
        findByGrievanceIdOrderByActionAtAsc(Long grievanceId);
}