package com.ofss.service;

import com.ofss.entity.Grievances;
import com.ofss.entity.GrievancesHistory;
import com.ofss.repository.GrievancesRepository;
import com.ofss.repository.GrievancesHistoryRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
public class GrievanceService {

    private final GrievancesRepository grievanceRepository;
    private final GrievancesHistoryRepository historyRepository;

    private static final Set<String> VALID_STATUSES = Set.of(
        "OPEN", "IN_PROGRESS", "RESOLVED",
        "ESCALATED", "CLOSED"
    );

    public GrievanceService(
            GrievancesRepository grievanceRepository,
            GrievancesHistoryRepository historyRepository) {
        this.grievanceRepository = grievanceRepository;
        this.historyRepository = historyRepository;
    }

    // --------------------------Submit a new grievance
    @Transactional
    public Grievances submitGrievance(Grievances grievance) {

        grievance.setStatus("OPEN");
        grievance.setResolutionRemarks(null);
        grievance.setAssignedTo(null);

        Grievances saved = grievanceRepository.save(grievance);

        saveHistory(
            saved.getGrievanceId(),
            "NEW",
            "OPEN",
            "Grievance submitted by borrower",
            saved.getBorrowerId()
        );

        return saved;
    }

    //--------------------- View all grievances
    @Transactional(readOnly = true)
    public List<Grievances> getAllGrievances() {
        return grievanceRepository.findAll();
    }

    //--------------- View one grievance
    @Transactional(readOnly = true)
    public Grievances getGrievance(Long id) {
        return grievanceRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Grievance not found"
            ));
    }

    //------------- View grievances submitted by a borrower
    @Transactional(readOnly = true)
    public List<Grievances> getByBorrower(Long borrowerId) {
        return grievanceRepository.findByBorrowerId(borrowerId);
    }

    // ----------------------View grievances assigned to an officer
    @Transactional(readOnly = true)
    public List<Grievances> getByOfficer(Long officerId) {
        return grievanceRepository.findByAssignedTo(officerId);
    }

    //----------------- Assign grievance to an officer
    @Transactional
    public Grievances assignGrievance(
            Long grievanceId,
            Long officerId,
            Long actionBy) {

        Grievances grievance = getGrievance(grievanceId);

        grievance.setAssignedTo(officerId);

        Grievances saved = grievanceRepository.save(grievance);

        saveHistory(
            grievanceId,
            grievance.getStatus(),
            grievance.getStatus(),
            "Assigned to officer " + officerId,
            actionBy
        );

        return saved;
    }

    // -----------------Update status and preserve history
    @Transactional
    public Grievances updateStatus(
            Long grievanceId,
            String newStatus,
            String remarks,
            Long actionBy) {

        Grievances grievance = getGrievance(grievanceId);

        if (newStatus == null ||
                !VALID_STATUSES.contains(newStatus)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Invalid grievance status"
            );
        }

        if ("RESOLVED".equals(newStatus) &&
                (remarks == null || remarks.isBlank())) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Resolution remarks are required"
            );
        }

        String oldStatus = grievance.getStatus();

        grievance.setStatus(newStatus);

        if (remarks != null && !remarks.isBlank()) {
            grievance.setResolutionRemarks(remarks);
        }

        grievance.setUpdatedAt(LocalDateTime.now());

        Grievances saved = grievanceRepository.save(grievance);

        saveHistory(
            grievanceId,
            oldStatus,
            newStatus,
            (remarks == null || remarks.isBlank())
                ? "Status changed to " + newStatus
                : remarks,
            actionBy
        );

        return saved;
    }

    //------------------ View the history of a grievance
    @Transactional(readOnly = true)
    public List<GrievancesHistory> getHistory(Long grievanceId) {

        getGrievance(grievanceId);

        return historyRepository
            .findByGrievanceIdOrderByActionAtAsc(grievanceId);
    }
    
    //1. Get grievances by status
    public List<Grievances> getByStatus(String status) {
        return grievanceRepository.findByStatusIgnoreCase(status);
    }

    // 2. Get grievances by loan account ID
    public List<Grievances> getByLoanId(Long loanId) {
        return grievanceRepository.findByLoanAccountId(loanId);
    }

    // 3. Get overdue grievances
    public List<Grievances> getOverdueGrievances() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(7);

        return grievanceRepository
                .findByStatusNotInAndCreatedAtBefore(
                        List.of("RESOLVED", "CLOSED"),
                        cutoff);
    }

    //------------------- Helper method to insert a history record
    private void saveHistory(
            Long grievanceId,
            String oldStatus,
            String newStatus,
            String remarks,
            Long actionBy) {

        GrievancesHistory history = new GrievancesHistory();

        history.setGrievanceId(grievanceId);
        history.setOldStatus(oldStatus);
        history.setNewStatus(newStatus);
        history.setActionRemarks(remarks);
        history.setActionBy(actionBy);

        historyRepository.save(history);
    }
}
