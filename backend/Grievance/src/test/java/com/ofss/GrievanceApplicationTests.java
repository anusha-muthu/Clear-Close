package com.ofss;

import com.ofss.entity.Grievances;
import com.ofss.entity.GrievancesHistory;
import com.ofss.repository.GrievancesRepository;
import com.ofss.service.GrievanceService;
import com.ofss.repository.GrievancesHistoryRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GrievanceApplicationTests {

    @Mock
    private GrievancesRepository grievanceRepository;

    @Mock
    private GrievancesHistoryRepository historyRepository;

    @InjectMocks
    private GrievanceService grievanceService;

    private Grievances grievance;

    @BeforeEach
    void setUp() {
        grievance = new Grievances();

        grievance.setGrievanceId(1L);
        grievance.setLoanAccountId(1001L);
        grievance.setBorrowerId(501L);
        grievance.setCategory("LOAN CLOSURE DELAY");
        grievance.setDescription(
                "My loan closure request has been delayed."
        );
        grievance.setStatus("OPEN");
        grievance.setAssignedTo(null);
        grievance.setResolutionRemarks(null);
        grievance.setCreatedAt(LocalDateTime.now());
        grievance.setUpdatedAt(LocalDateTime.now());

           }
    private void stubSaveOperations() {
        when(grievanceRepository.save(any(Grievances.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(historyRepository.save(any(GrievancesHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    // 1. Submit a new grievance
    @Test
    void testSubmitGrievance() {
    	stubSaveOperations();
        Grievances result =
                grievanceService.submitGrievance(grievance);

        assertEquals("OPEN", result.getStatus());
        assertNull(result.getAssignedTo());
        assertNull(result.getResolutionRemarks());

        verify(grievanceRepository).save(grievance);
        verify(historyRepository).save(
                any(GrievancesHistory.class)
        );
    }

    // 2. Get all grievances
    @Test
    void testGetAllGrievances() {
        when(grievanceRepository.findAll())
                .thenReturn(List.of(grievance));

        List<Grievances> result =
                grievanceService.getAllGrievances();

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getGrievanceId());

        verify(grievanceRepository).findAll();
    }

    // 3. Get one grievance
    @Test
    void testGetGrievance() {
        when(grievanceRepository.findById(1L))
                .thenReturn(Optional.of(grievance));

        Grievances result =
                grievanceService.getGrievance(1L);

        assertEquals(1L, result.getGrievanceId());
        assertEquals("OPEN", result.getStatus());
    }

    // 4. Grievance not found
    @Test
    void testGetGrievanceNotFound() {
        when(grievanceRepository.findById(99L))
                .thenReturn(Optional.empty());

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> grievanceService.getGrievance(99L)
                );

        assertEquals(
                HttpStatus.NOT_FOUND,
                exception.getStatusCode()
        );
    }

    // 5. Get grievances by borrower
    @Test
    void testGetByBorrower() {
        when(grievanceRepository.findByBorrowerId(501L))
                .thenReturn(List.of(grievance));

        List<Grievances> result =
                grievanceService.getByBorrower(501L);

        assertEquals(1, result.size());
        verify(grievanceRepository).findByBorrowerId(501L);
    }

    // 6. Get grievances assigned to an officer
    @Test
    void testGetByOfficer() {
        grievance.setAssignedTo(101L);

        when(grievanceRepository.findByAssignedTo(101L))
                .thenReturn(List.of(grievance));

        List<Grievances> result =
                grievanceService.getByOfficer(101L);

        assertEquals(1, result.size());
        verify(grievanceRepository).findByAssignedTo(101L);
    }

    // 7. Assign grievance to an officer
    @Test
    void testAssignGrievance() {
    	stubSaveOperations();
        when(grievanceRepository.findById(1L))
                .thenReturn(Optional.of(grievance));

        Grievances result =
                grievanceService.assignGrievance(1L, 101L, 101L);

        assertEquals(101L, result.getAssignedTo());

        ArgumentCaptor<GrievancesHistory> captor =
                ArgumentCaptor.forClass(GrievancesHistory.class);

        verify(historyRepository).save(captor.capture());

        GrievancesHistory history = captor.getValue();

        assertEquals(1L, history.getGrievanceId());
        assertEquals("Assigned to officer 101",
                history.getActionRemarks());
        assertEquals(101L, history.getActionBy());
    }

    // 8. Update status and save history
    @Test
    void testUpdateStatus() {
    	stubSaveOperations();
        when(grievanceRepository.findById(1L))
                .thenReturn(Optional.of(grievance));

        Grievances result = grievanceService.updateStatus(
                1L,
                "IN_PROGRESS",
                "Review started",
                101L
        );

        assertEquals("IN_PROGRESS", result.getStatus());
        assertEquals("Review started",
                result.getResolutionRemarks());

        verify(grievanceRepository).save(grievance);

        ArgumentCaptor<GrievancesHistory> captor =
                ArgumentCaptor.forClass(GrievancesHistory.class);

        verify(historyRepository).save(captor.capture());

        assertEquals("OPEN", captor.getValue().getOldStatus());
        assertEquals("IN_PROGRESS",
                captor.getValue().getNewStatus());
        assertEquals("Review started",
                captor.getValue().getActionRemarks());
    }

    // 9. Reject an invalid status
    @Test
    void testUpdateStatusWithInvalidStatus() {
        when(grievanceRepository.findById(1L))
                .thenReturn(Optional.of(grievance));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> grievanceService.updateStatus(
                        1L, "INVALID", "Test", 101L
                )
        );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                exception.getStatusCode()
        );

        verify(grievanceRepository, never())
                .save(any(Grievances.class));

        verify(historyRepository, never())
                .save(any(GrievancesHistory.class));
    }

    // 10. Resolution must include remarks
    @Test
    void testResolveWithoutRemarks() {
        when(grievanceRepository.findById(1L))
                .thenReturn(Optional.of(grievance));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> grievanceService.updateStatus(
                        1L, "RESOLVED", "  ", 101L
                )
        );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                exception.getStatusCode()
        );

        verify(grievanceRepository, never())
                .save(any(Grievances.class));
    }

    // 11. Get grievance history
    @Test
    void testGetHistory() {
        GrievancesHistory history = new GrievancesHistory();
        history.setGrievanceId(1L);
        history.setOldStatus("OPEN");
        history.setNewStatus("IN_PROGRESS");
        history.setActionRemarks("Review started");
        history.setActionBy(101L);

        when(grievanceRepository.findById(1L))
                .thenReturn(Optional.of(grievance));

        when(historyRepository
                .findByGrievanceIdOrderByActionAtAsc(1L))
                .thenReturn(List.of(history));

        List<GrievancesHistory> result =
                grievanceService.getHistory(1L);

        assertEquals(1, result.size());
        assertEquals("IN_PROGRESS",
                result.get(0).getNewStatus());
    }

    // 12. Get grievances by status
    @Test
    void testGetByStatus() {
        when(grievanceRepository.findByStatusIgnoreCase("OPEN"))
                .thenReturn(List.of(grievance));

        List<Grievances> result =
                grievanceService.getByStatus("OPEN");

        assertEquals(1, result.size());
        verify(grievanceRepository)
                .findByStatusIgnoreCase("OPEN");
    }

    // 13. Get grievances by loan account ID
    @Test
    void testGetByLoanId() {
        when(grievanceRepository.findByLoanAccountId(1001L))
                .thenReturn(List.of(grievance));

        List<Grievances> result =
                grievanceService.getByLoanId(1001L);

        assertEquals(1, result.size());
        verify(grievanceRepository)
                .findByLoanAccountId(1001L);
    }

    // 14. Get overdue grievances
    @Test
    void testGetOverdueGrievances() {
        when(grievanceRepository
                .findByStatusNotInAndCreatedAtBefore(
                        anyList(),
                        any(LocalDateTime.class)))
                .thenReturn(List.of(grievance));

        List<Grievances> result =
                grievanceService.getOverdueGrievances();

        assertEquals(1, result.size());

        verify(grievanceRepository)
                .findByStatusNotInAndCreatedAtBefore(
                        eq(List.of("RESOLVED", "CLOSED")),
                        any(LocalDateTime.class)
                );
    }
}