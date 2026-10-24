package com.ofss;

import com.ofss.dto.AuditEvent;
import com.ofss.entity.Grievances;
import com.ofss.entity.GrievancesHistory;
import com.ofss.repository.GrievancesHistoryRepository;
import com.ofss.repository.GrievancesRepository;
import com.ofss.service.GrievanceService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the supplied GrievanceService.
 * No Spring context, database or Kafka broker is started.
 * Transaction/locking enforcement and repository query semantics require
 * separate integration tests against the application's database.
 */
@ExtendWith(MockitoExtension.class)
class GrievanceApplicationTests {

    private static final long BORROWER = 2L;
    private static final long OFFICER = 3L;
    private static final long SENIOR = 22L;
    private static final long LOAN = 83L;
    private static final long GRIEVANCE = 19L;
    private static final List<String> ACTIVE =
            List.of("OPEN", "IN_PROGRESS", "ESCALATED");

    @Mock private GrievancesRepository grievanceRepository;
    @Mock private GrievancesHistoryRepository historyRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private EntityManager entityManager;
    @InjectMocks
    private GrievanceService service;

    private Grievances grievance;

    @BeforeEach
    void setUp() {
        service = new GrievanceService(
                grievanceRepository, historyRepository, eventPublisher);
        // Mockito constructor injection does not also inject @PersistenceContext.
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
        officerQuery = null;
        currentOfficerLookup = null;
        officerRows.clear();

        grievance = new Grievances();
        grievance.setGrievanceId(GRIEVANCE);
        grievance.setBorrowerId(BORROWER);
        grievance.setLoanAccountId(LOAN);
        grievance.setCategory("DELAY");
        grievance.setDescription("Collateral release is delayed.");
        grievance.setStatus("OPEN");
        grievance.setAssignedTo(OFFICER);
        grievance.setCreatedAt(LocalDateTime.now().minusDays(1));
    }

    // Submission

    @Test
    void submitCreatesFreshOpenRecordAndPublishesAudit() {
        stubEligibleLoan(BORROWER, 1L);
        stubNoActiveGrievance();
        Query selection = stubCategorySelection(List.of(OFFICER));
        stubSave();

        // Borrower-supplied administrative fields must be ignored.
        Grievances input = input();
        input.setGrievanceId(999L);
        input.setBorrowerId(999L);
        input.setAssignedTo(999L);
        input.setStatus("CLOSED");
        input.setResolutionRemarks("Injected remarks");

        Grievances result = service.submitGrievance(input, BORROWER, "BORROWER");

        assertNotSame(input, result);
        assertEquals(Long.valueOf(100L), result.getGrievanceId());
        assertEquals(Long.valueOf(BORROWER), result.getBorrowerId());
        assertEquals(Long.valueOf(LOAN), result.getLoanAccountId());
        assertEquals(Long.valueOf(OFFICER), result.getAssignedTo());
        assertEquals("OPEN", result.getStatus());
        assertEquals("DELAY", result.getCategory());
        assertEquals("Collateral release is delayed.", result.getDescription());
        assertNull(result.getResolutionRemarks());
        verify(selection).setParameter("role", "COLLATERAL_OFFICER");
        verify(selection).setParameter("borrowerId", BORROWER);
        verify(grievanceRepository).save(any(Grievances.class));

        GrievancesHistory history = capturedHistory();
        assertNull(history.getOldStatus());
        assertEquals("OPEN", history.getNewStatus());
        assertEquals(Long.valueOf(BORROWER), history.getActionBy());

        AuditEvent audit = capturedAudit();
        assertEquals("GRIEVANCE_SUBMITTED", audit.action());
        assertEquals(Long.valueOf(BORROWER), audit.userId());
        assertEquals(result.getGrievanceId(), audit.grievanceId());
        assertNull(audit.officerId());
    }

    @Test
    void submitAllowsLoanWhenRepositoryReportsNoActiveGrievances() {
        // Covers the service path used when previous grievances are CLOSED.
        // Actual CLOSED exclusion by the derived query needs a repository test.
        stubEligibleLoan(BORROWER, 1L);
        stubNoActiveGrievance();
        stubCategorySelection(List.of(OFFICER));
        stubSave();

        Grievances saved = service.submitGrievance(input(), BORROWER, "BORROWER");

        assertEquals("OPEN", saved.getStatus());
        verify(grievanceRepository).existsByLoanAccountIdAndStatusIn(LOAN, ACTIVE);
        assertFalse(ACTIVE.contains("CLOSED"));
    }

    @Test
    void submitBlocksLoanWithAnActiveGrievance() {
        stubEligibleLoan(BORROWER, 1L);
        when(grievanceRepository.existsByLoanAccountIdAndStatusIn(LOAN, ACTIVE))
                .thenReturn(true);

        assertStatus(HttpStatus.CONFLICT,
                () -> service.submitGrievance(input(), BORROWER, "BORROWER"));
        verifyNoWrites();
    }

    @Test
    void submitRejectsWrongAccountType() {
        assertStatus(HttpStatus.FORBIDDEN,
                () -> service.submitGrievance(input(), OFFICER, "OFFICER"));
        verifyNoInteractions(entityManager);
        verifyNoWrites();
    }

    @Test
    void submitRejectsNullInput() {
        assertStatus(HttpStatus.BAD_REQUEST,
                () -> service.submitGrievance(null, BORROWER, "BORROWER"));
        verifyNoWrites();
    }

    @Test
    void submitRejectsInvalidLoanId() {
        Grievances input = input();
        input.setLoanAccountId(0L);
        assertStatus(HttpStatus.BAD_REQUEST,
                () -> service.submitGrievance(input, BORROWER, "BORROWER"));
        verifyNoWrites();
    }

    @Test
    void submitRejectsBlankDescription() {
        Grievances input = input();
        input.setDescription("  ");
        assertStatus(HttpStatus.BAD_REQUEST,
                () -> service.submitGrievance(input, BORROWER, "BORROWER"));
        verifyNoWrites();
    }

    @Test
    void submitRejectsMissingLoan() {
        Query ownership = nativeQuery("SELECT BORROWER_ID FROM LOAN_ACCOUNT");
        when(ownership.getResultList()).thenReturn(List.of());
        assertStatus(HttpStatus.NOT_FOUND,
                () -> service.submitGrievance(input(), BORROWER, "BORROWER"));
        verifyNoWrites();
    }

    @Test
    void submitRejectsLoanOwnedByAnotherBorrower() {
        Query ownership = nativeQuery("SELECT BORROWER_ID FROM LOAN_ACCOUNT");
        when(ownership.getResultList()).thenReturn(List.of(999L));
        assertStatus(HttpStatus.FORBIDDEN,
                () -> service.submitGrievance(input(), BORROWER, "BORROWER"));
        verifyNoWrites();
    }

    @Test
    void submitRejectsInactiveBorrower() {
        stubEligibleLoan(BORROWER, 0L);
        assertStatus(HttpStatus.FORBIDDEN,
                () -> service.submitGrievance(input(), BORROWER, "BORROWER"));
        verifyNoWrites();
    }

    @Test
    void submitEscalatesToSeniorWhenCategoryOfficerUnavailable() {
        stubEligibleLoan(BORROWER, 1L);
        stubNoActiveGrievance();
        stubCategorySelection(List.of());
        stubSeniorSelection(List.of(SENIOR));
        stubSave();

        Grievances result = service.submitGrievance(input(), BORROWER, "BORROWER");

        assertEquals("ESCALATED", result.getStatus());
        assertEquals(Long.valueOf(SENIOR), result.getAssignedTo());
        verify(grievanceRepository, times(2)).save(any(Grievances.class));
        ArgumentCaptor<GrievancesHistory> history =
                ArgumentCaptor.forClass(GrievancesHistory.class);
        verify(historyRepository, times(2)).save(history.capture());
        assertEquals("OPEN", history.getAllValues().get(0).getNewStatus());
        assertEquals("ESCALATED", history.getAllValues().get(1).getNewStatus());
        ArgumentCaptor<AuditEvent> events = ArgumentCaptor.forClass(AuditEvent.class);
        verify(eventPublisher, times(2)).publishEvent(events.capture());
        assertEquals("GRIEVANCE_SUBMITTED", events.getAllValues().get(0).action());
        assertEquals("GRIEVANCE_ESCALATED_NO_CATEGORY_OFFICER",
                events.getAllValues().get(1).action());
    }

    @Test
    void submitRemainsUnassignedWhenNoEligibleOfficersExist() {
        stubEligibleLoan(BORROWER, 1L);
        stubNoActiveGrievance();
        stubCategorySelection(List.of());
        stubSeniorSelection(List.of());
        stubSave();

        Grievances result = service.submitGrievance(input(), BORROWER, "BORROWER");

        assertEquals("ESCALATED", result.getStatus());
        assertNull(result.getAssignedTo());
        verify(historyRepository, times(2)).save(any(GrievancesHistory.class));
    }

    // Officer actions

    @Test
    void updateStatusSavesHistoryAndActingOfficerAudit() {
        stubOfficer(OFFICER, null, "COLLATERAL_OFFICER");
        stubLockedGrievance();
        stubSave();

        Grievances result = service.updateStatus(
                GRIEVANCE, "IN_PROGRESS", "  Review started  ", OFFICER, "OFFICER");

        assertEquals("IN_PROGRESS", result.getStatus());
        assertEquals("Review started", result.getResolutionRemarks());
        GrievancesHistory history = capturedHistory();
        assertEquals("OPEN", history.getOldStatus());
        assertEquals("IN_PROGRESS", history.getNewStatus());
        assertEquals("Review started", history.getActionRemarks());
        assertEquals(Long.valueOf(OFFICER), history.getActionBy());
        AuditEvent audit = capturedAudit();
        assertEquals("GRIEVANCE_STATUS_CHANGED_TO_IN_PROGRESS", audit.action());
        assertEquals(Long.valueOf(OFFICER), audit.officerId());
        verify(entityManager).refresh(grievance, LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void updateStatusRejectsUnassignedOfficer() {
        stubOfficer(OFFICER, null, "COLLATERAL_OFFICER");
        grievance.setAssignedTo(99L);
        stubLockedGrievance();
        assertStatus(HttpStatus.FORBIDDEN,
                () -> service.updateStatus(GRIEVANCE, "IN_PROGRESS", "Review", OFFICER));
        verifyNoWrites();
    }

    @Test
    void updateStatusRejectsSelfReview() {
        stubOfficer(OFFICER, BORROWER, "COLLATERAL_OFFICER");
        stubLockedGrievance();
        assertStatus(HttpStatus.FORBIDDEN,
                () -> service.updateStatus(GRIEVANCE, "IN_PROGRESS", "Review", OFFICER));
        verifyNoWrites();
    }

    @Test
    void updateStatusRejectsInactiveOfficer() {
        Query query = nativeQuery("SELECT OFFICER_ID, USER_ID, ROLE FROM OFFICER");
        when(query.getResultList()).thenReturn(List.of());
        assertStatus(HttpStatus.FORBIDDEN,
                () -> service.updateStatus(GRIEVANCE, "IN_PROGRESS", "Review", OFFICER));
        verifyNoWrites();
    }

    @Test
    void updateStatusRejectsMissingGrievance() {
        stubOfficer(OFFICER, null, "COLLATERAL_OFFICER");
        assertStatus(HttpStatus.NOT_FOUND,
                () -> service.updateStatus(GRIEVANCE, "IN_PROGRESS", "Review", OFFICER));
        verifyNoWrites();
    }

    @Test
    void updateStatusRejectsUnknownStatus() {
        stubOfficer(OFFICER, null, "COLLATERAL_OFFICER");
        stubLockedGrievance();
        assertStatus(HttpStatus.BAD_REQUEST,
                () -> service.updateStatus(GRIEVANCE, "INVALID", "Review", OFFICER));
        verifyNoWrites();
    }

    @Test
    void updateStatusRejectsTransitionFromClosed() {
        stubOfficer(OFFICER, null, "COLLATERAL_OFFICER");
        grievance.setStatus("CLOSED");
        stubLockedGrievance();
        assertStatus(HttpStatus.CONFLICT,
                () -> service.updateStatus(GRIEVANCE, "IN_PROGRESS", "Reopen", OFFICER));
        verifyNoWrites();
    }

    @Test
    void inProgressGrievanceCanBeClosed() {
        // Requires the latest agreed policy: IN_PROGRESS -> CLOSED is allowed.
        stubOfficer(OFFICER, null, "COLLATERAL_OFFICER");
        grievance.setStatus("IN_PROGRESS");
        stubLockedGrievance();
        stubSave();

        Grievances result = service.updateStatus(
                GRIEVANCE, "CLOSED", "Issue addressed", OFFICER);

        assertEquals("CLOSED", result.getStatus());
        assertEquals("IN_PROGRESS", capturedHistory().getOldStatus());
        assertEquals("GRIEVANCE_STATUS_CHANGED_TO_CLOSED", capturedAudit().action());
    }

    @Test
    void assignedSeniorCanCloseEscalatedGrievance() {
        stubOfficer(SENIOR, null, "SENIOR_GRIEVANCE_OFFICER");
        grievance.setAssignedTo(SENIOR);
        grievance.setStatus("ESCALATED");
        stubLockedGrievance();
        stubSave();

        Grievances result = service.updateStatus(
                GRIEVANCE, "CLOSED", "Senior review completed", SENIOR);

        assertEquals("CLOSED", result.getStatus());
        assertEquals(Long.valueOf(SENIOR), capturedAudit().officerId());
    }

    @Test
    void updateStatusRejectsBlankRemarks() {
        stubOfficer(OFFICER, null, "COLLATERAL_OFFICER");
        stubLockedGrievance();
        assertStatus(HttpStatus.BAD_REQUEST,
                () -> service.updateStatus(GRIEVANCE, "IN_PROGRESS", "   ", OFFICER));
        verifyNoWrites();
    }

    @Test
    void updateStatusRejectsRemarksOver1000Characters() {
        stubOfficer(OFFICER, null, "COLLATERAL_OFFICER");
        stubLockedGrievance();
        assertStatus(HttpStatus.BAD_REQUEST,
                () -> service.updateStatus(GRIEVANCE, "IN_PROGRESS", "x".repeat(1001), OFFICER));
        verifyNoWrites();
    }

    @Test
    void escalationAssignsSeniorAndRecordsOriginalActingOfficer() {
        stubOfficer(OFFICER, null, "COLLATERAL_OFFICER");
        stubLockedGrievance();
        stubSeniorSelection(List.of(SENIOR));
        stubSave();

        Grievances result = service.updateStatus(
                GRIEVANCE, "ESCALATED", "Senior review needed", OFFICER);

        assertEquals("ESCALATED", result.getStatus());
        assertEquals(Long.valueOf(SENIOR), result.getAssignedTo());
        assertEquals(Long.valueOf(OFFICER), capturedAudit().officerId());
        assertEquals("ESCALATED", capturedHistory().getNewStatus());
    }

    @Test
    void seniorCanAssignAnUnassignedGrievanceToCategoryOfficer() {
        stubOfficer(SENIOR, null, "SENIOR_GRIEVANCE_OFFICER");
        stubOfficer(OFFICER, null, "COLLATERAL_OFFICER");
        grievance.setAssignedTo(null);
        stubLockedGrievance();
        stubSave();

        Grievances result = service.assignGrievance(GRIEVANCE, OFFICER, SENIOR, "OFFICER");

        assertEquals(Long.valueOf(OFFICER), result.getAssignedTo());
        assertEquals("OPEN", result.getStatus());
        GrievancesHistory history = capturedHistory();
        assertEquals("Senior officer reassigned from null to 3", history.getActionRemarks());
        assertEquals(Long.valueOf(SENIOR), history.getActionBy());
        assertEquals("GRIEVANCE_ASSIGNED", capturedAudit().action());
    }

    @Test
    void categoryOfficerCannotPerformSeniorAssignment() {
        stubOfficer(OFFICER, null, "COLLATERAL_OFFICER");
        assertStatus(HttpStatus.FORBIDDEN,
                () -> service.assignGrievance(GRIEVANCE, OFFICER, OFFICER, "OFFICER"));
        verifyNoWrites();
    }

    @Test
    void closedGrievanceCannotBeReassigned() {
        stubOfficer(SENIOR, null, "SENIOR_GRIEVANCE_OFFICER");
        grievance.setAssignedTo(SENIOR);
        grievance.setStatus("CLOSED");
        stubLockedGrievance();
        assertStatus(HttpStatus.CONFLICT,
                () -> service.assignGrievance(GRIEVANCE, OFFICER, SENIOR, "OFFICER"));
        verifyNoWrites();
    }

    @Test
    void assignmentRejectsOfficerWithWrongCategoryRole() {
        stubOfficer(SENIOR, null, "SENIOR_GRIEVANCE_OFFICER");
        stubOfficer(OFFICER, null, "LOAN_CLOSURE_OFFICER");
        grievance.setAssignedTo(null);
        stubLockedGrievance();
        assertStatus(HttpStatus.BAD_REQUEST,
                () -> service.assignGrievance(GRIEVANCE, OFFICER, SENIOR, "OFFICER"));
        verifyNoWrites();
    }

    @Test
    void assigningSameOfficerDoesNotCreateHistoryOrAudit() {
        stubOfficer(SENIOR, null, "SENIOR_GRIEVANCE_OFFICER");
        stubOfficer(OFFICER, null, "COLLATERAL_OFFICER");
        grievance.setStatus("ESCALATED");
        stubLockedGrievance();

        assertSame(grievance, service.assignGrievance(GRIEVANCE, OFFICER, SENIOR));
        verifyNoWrites();
    }

    // Queue filtering and scheduled escalation

    @Test
    void officerQueueExcludesOwnBorrowerGrievances() {
        stubOfficer(OFFICER, BORROWER, "COLLATERAL_OFFICER");
        Grievances other = input();
        other.setGrievanceId(20L);
        other.setBorrowerId(99L);
        when(grievanceRepository.findByAssignedTo(OFFICER))
                .thenReturn(List.of(grievance, other));

        assertEquals(List.of(other), service.getByOfficer(OFFICER));
    }

    @Test
    void seniorQueueExcludesOwnAndUnrelatedAssignedGrievances() {
        stubOfficer(SENIOR, 99L, "SENIOR_GRIEVANCE_OFFICER");
        Grievances own = input();
        own.setBorrowerId(99L);
        own.setStatus("ESCALATED");
        grievance.setStatus("ESCALATED");
        Grievances unrelated = input();
        unrelated.setStatus("OPEN");
        unrelated.setAssignedTo(OFFICER);
        when(grievanceRepository.findAll()).thenReturn(List.of(own, grievance, unrelated));

        assertEquals(List.of(grievance), service.getSeniorQueue(SENIOR, "OFFICER"));
    }

    @Test
    void overdueGrievanceEscalatesAndCreatesSystemHistory() {
        Query candidates = nativeQuery("SELECT g.GRIEVANCE_ID FROM GRIEVANCES g");
        when(candidates.getResultList()).thenReturn(List.of(GRIEVANCE));
        grievance.setCreatedAt(LocalDateTime.now().minusDays(6));
        stubLockedGrievance();
        stubSeniorSelection(List.of(SENIOR));
        stubSave();

        assertEquals(1, service.escalateOverdueGrievances());
        assertEquals("ESCALATED", grievance.getStatus());
        assertEquals(Long.valueOf(SENIOR), grievance.getAssignedTo());
        assertNull(capturedHistory().getActionBy());
        assertEquals("GRIEVANCE_AUTO_ESCALATED", capturedAudit().action());
    }

    @Test
    void escalationRechecksCandidateStatusAfterLocking() {
        Query candidates = nativeQuery("SELECT g.GRIEVANCE_ID FROM GRIEVANCES g");
        when(candidates.getResultList()).thenReturn(List.of(GRIEVANCE));
        grievance.setCreatedAt(LocalDateTime.now().minusDays(6));
        grievance.setStatus("CLOSED");
        stubLockedGrievance();

        assertEquals(0, service.escalateOverdueGrievances());
        verifyNoWrites();
    }

    @Test
    void missingGrievanceReturnsNotFound() {
        when(grievanceRepository.findById(GRIEVANCE)).thenReturn(Optional.empty());
        assertStatus(HttpStatus.NOT_FOUND, () -> service.getGrievance(GRIEVANCE));
    }

    @Test
    void overdueLookupExcludesCompletedAndAlreadyEscalatedStatuses() {
        when(grievanceRepository.findByStatusNotInAndCreatedAtBefore(
                eq(List.of("RESOLVED", "REJECTED", "CLOSED", "ESCALATED")),
                any(LocalDateTime.class))).thenReturn(List.of(grievance));

        LocalDateTime before = LocalDateTime.now().minusDays(5);
        assertEquals(List.of(grievance), service.getOverdueGrievances());
        LocalDateTime after = LocalDateTime.now().minusDays(5);

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(grievanceRepository).findByStatusNotInAndCreatedAtBefore(
                eq(List.of("RESOLVED", "REJECTED", "CLOSED", "ESCALATED")), cutoff.capture());
        assertFalse(cutoff.getValue().isBefore(before));
        assertFalse(cutoff.getValue().isAfter(after));
    }

    // Helpers: mock native-query results, not the service's private Officer record.

    private Grievances input() {
        Grievances input = new Grievances();
        input.setBorrowerId(BORROWER);
        input.setLoanAccountId(LOAN);
        input.setCategory("DELAY");
        input.setDescription("  Collateral release is delayed.  ");
        return input;
    }

    private Query nativeQuery(String sqlFragment) {
        Query query = mock(Query.class, RETURNS_SELF);
        when(entityManager.createNativeQuery(contains(sqlFragment))).thenReturn(query);
        return query;
    }

    private void stubEligibleLoan(Long owner, long activeBorrowerCount) {
        Query ownership = nativeQuery("SELECT BORROWER_ID FROM LOAN_ACCOUNT");
        when(ownership.getResultList()).thenReturn(List.of(owner));
        Query borrower = nativeQuery("SELECT COUNT(*) FROM BORROWERS");
        when(borrower.getSingleResult()).thenReturn(activeBorrowerCount);
    }

    private void stubNoActiveGrievance() {
        when(grievanceRepository.existsByLoanAccountIdAndStatusIn(LOAN, ACTIVE))
                .thenReturn(false);
    }

    private Query stubCategorySelection(List<Long> officerIds) {
        Query query = nativeQuery("o.ROLE = :role");
        when(query.getResultList()).thenReturn(officerIds);
        return query;
    }

    private void stubSeniorSelection(List<Long> officerIds) {
        Query query = nativeQuery("o.ROLE = 'SENIOR_GRIEVANCE_OFFICER'");
        when(query.getResultList()).thenReturn(officerIds);
    }

    private void stubOfficer(Long officerId, Long borrowerId, String role) {
        // Reuse one lookup query when a test checks both senior and target.
        // Each setParameter call selects the correct result for that officer.
        if (officerQuery == null) {
            officerQuery = nativeQuery("SELECT OFFICER_ID, USER_ID, ROLE FROM OFFICER");
            when(officerQuery.setParameter(eq("id"), any())).thenAnswer(invocation -> {
                currentOfficerLookup = (Long) invocation.getArgument(1);
                return officerQuery;
            });
            when(officerQuery.getResultList()).thenAnswer(invocation -> {
                Object[] row = officerRows.get(currentOfficerLookup);
                return row == null ? List.of() : java.util.Collections.singletonList(row);
            });
        }
        officerRows.put(officerId, new Object[]{officerId, borrowerId, role});
    }

    private Query officerQuery;
    private Long currentOfficerLookup;
    private final java.util.Map<Long, Object[]> officerRows = new java.util.HashMap<>();

    private void stubLockedGrievance() {
        when(entityManager.find(Grievances.class, GRIEVANCE, LockModeType.PESSIMISTIC_WRITE))
                .thenReturn(grievance);
    }

    private void stubSave() {
        when(grievanceRepository.save(any(Grievances.class))).thenAnswer(invocation -> {
            Grievances saved = invocation.getArgument(0);
            if (saved.getGrievanceId() == null) saved.setGrievanceId(100L);
            return saved;
        });
    }

    private GrievancesHistory capturedHistory() {
        ArgumentCaptor<GrievancesHistory> captor = ArgumentCaptor.forClass(GrievancesHistory.class);
        verify(historyRepository).save(captor.capture());
        return captor.getValue();
    }

    private AuditEvent capturedAudit() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        return captor.getValue();
    }

    private void verifyNoWrites() {
        verify(grievanceRepository, never()).save(any(Grievances.class));
        verifyNoInteractions(historyRepository, eventPublisher);
    }

    private void assertStatus(HttpStatus expected, Runnable action) {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, action::run);
        assertEquals(expected, exception.getStatusCode());
    }
}