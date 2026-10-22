package com.clearclose;

import com.clearclose.client.LoanAccountClient;
import com.clearclose.client.NocClient;
import com.clearclose.dto.CollateralLoanResponse;
import com.clearclose.entity.Collateral;
import com.clearclose.entity.CollateralReleaseRequests;
import com.clearclose.exception.ResourceNotFoundException;
import com.clearclose.repository.CollateralReleaseRepository;
import com.clearclose.repository.CollateralRepository;
import com.clearclose.service.CollateralReleaseService;
import com.clearclose.service.CollateralService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CollateralApplicationTests {

    @Mock
    private CollateralRepository collateralRepository;

    @Mock
    private CollateralReleaseRepository collateralReleaseRepository;

    @Mock
    private LoanAccountClient loanClient;
    
    @Mock
    private ApplicationEventPublisher eventPublisher;
    
    @InjectMocks
    private CollateralReleaseService releaseService;

    @Mock
    private NocClient nocClient;

    @InjectMocks
    private CollateralService collateralService;

    private Collateral collateral;

    @BeforeEach
    void setUp() {
        collateral = new Collateral();
        collateral.setCollateralId(10L);
        collateral.setLoanAccountId(1L);
        collateral.setCollateralType("PROPERTY");
        collateral.setReleaseStatus("NOT_ELIGIBLE");
    }

    @Test
    void getCollateralTypesReturnsSupportedTypes() {
        List<String> types = collateralService.getCollateralTypes();

        assertEquals(5, types.size());
        assertTrue(types.contains("PROPERTY"));
        assertTrue(types.contains("GOLD"));
        assertTrue(types.contains("VEHICLE"));
        assertTrue(types.contains("FIXED_DEPOSIT"));
        assertTrue(types.contains("OTHER"));
    }

    @Test
    void createCollateralSetsInitialReleaseStatus() {
        when(collateralRepository.save(any(Collateral.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Collateral result = collateralService.createCollateral(collateral);

        assertEquals("NOT_ELIGIBLE", result.getReleaseStatus());
        verify(collateralRepository).save(collateral);
    }

    @Test
    void getCollateralByIdReturnsCollateral() {
        when(collateralRepository.findById(10L))
                .thenReturn(Optional.of(collateral));

        Collateral result = collateralService.getCollateralById(10L);

        assertEquals(10L, result.getCollateralId());
        assertEquals(1L, result.getLoanAccountId());
    }

    @Test
    void getCollateralByIdThrowsWhenMissing() {
        when(collateralRepository.findById(99L))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> collateralService.getCollateralById(99L)
        );
    }

    @Test
    void getCollateralByLoanIdReturnsMatchingCollateral() {
        when(collateralRepository.findByLoanAccountId(1L))
                .thenReturn(List.of(collateral));

        List<Collateral> result =
                collateralService.getCollateralByLoanId(1L);

        assertEquals(1, result.size());
        assertEquals(10L, result.get(0).getCollateralId());
    }

    @Test
    void getCollateralByLoanIdThrowsWhenEmpty() {
        when(collateralRepository.findByLoanAccountId(99L))
                .thenReturn(List.of());

        assertThrows(
                ResourceNotFoundException.class,
                () -> collateralService.getCollateralByLoanId(99L)
        );
    }

    @Test
    void checkEligibilityAllowsRequestWhenLoanClosedSettledAndNocIssued() {
        CollateralLoanResponse loan = mock(CollateralLoanResponse.class);

        when(collateralRepository.findById(10L))
                .thenReturn(Optional.of(collateral));
        when(loanClient.getByLoanId(1L, "Bearer demo-token"))
                .thenReturn(loan);
        when(loan.getLoanStatus()).thenReturn("CLOSED");
        when(loan.getOutstandingAmount()).thenReturn(BigDecimal.ZERO);
        when(nocClient.hasAvailableNoc(1L, "Bearer demo-token"))
                .thenReturn(true);
        when(collateralReleaseRepository
                .findByCollateralIdAndStatusIn(eq(10L), anyList()))
                .thenReturn(List.of());

        Map<String, Object> result =
                collateralService.checkEligibility(10L, "Bearer demo-token");

        assertEquals(true, result.get("eligible"));
        assertEquals(true, result.get("canSubmitRequest"));
        assertEquals(true, result.get("nocIssued"));
        assertEquals("NONE", result.get("requestStatus"));
    }

    @Test
    void checkEligibilityDoesNotAllowRequestWhenLoanIsOpen() {
        CollateralLoanResponse loan = mock(CollateralLoanResponse.class);

        when(collateralRepository.findById(10L))
                .thenReturn(Optional.of(collateral));
        when(loanClient.getByLoanId(1L, "Bearer demo-token"))
                .thenReturn(loan);
        when(loan.getLoanStatus()).thenReturn("ACTIVE");
        when(loan.getOutstandingAmount()).thenReturn(BigDecimal.ZERO);

        Map<String, Object> result =
                collateralService.checkEligibility(10L, "Bearer demo-token");

        assertEquals(false, result.get("eligible"));
        assertEquals(false, result.get("canSubmitRequest"));
        assertEquals(
                "Loan must be closed before collateral release",
                result.get("message")
        );

        verify(nocClient).hasAvailableNoc(1L, "Bearer demo-token");
    }

    @Test
    void checkEligibilityDoesNotAllowRequestWhenNocIsPending() {
        CollateralLoanResponse loan = mock(CollateralLoanResponse.class);

        when(collateralRepository.findById(10L))
                .thenReturn(Optional.of(collateral));
        when(loanClient.getByLoanId(1L, "Bearer demo-token"))
                .thenReturn(loan);
        when(loan.getLoanStatus()).thenReturn("CLOSED");
        when(loan.getOutstandingAmount()).thenReturn(BigDecimal.ZERO);
        when(nocClient.hasAvailableNoc(1L, "Bearer demo-token"))
                .thenReturn(false);

        Map<String, Object> result =
                collateralService.checkEligibility(10L, "Bearer demo-token");

        assertEquals(false, result.get("eligible"));
        assertEquals(false, result.get("canSubmitRequest"));
        assertEquals(
                "NOC is still pending. Release can be requested after issuance.",
                result.get("message")
        );
    }

    @Test
    void checkEligibilityThrowsWhenCollateralDoesNotExist() {
        when(collateralRepository.findById(999L))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> collateralService.checkEligibility(999L, "Bearer demo-token")
        );

        verifyNoInteractions(loanClient, nocClient);
    }

    @Test
    void checkEligibilityThrowsBadGatewayWhenLoanServiceReturnsNull() {
        when(collateralRepository.findById(10L))
                .thenReturn(Optional.of(collateral));
        when(loanClient.getByLoanId(1L, "Bearer demo-token"))
                .thenReturn(null);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> collateralService.checkEligibility(10L, "Bearer demo-token")
        );

        assertEquals(502, exception.getStatusCode().value());
    }

    @Test
    void assertLoanOwnedByBorrowerAllowsCorrectOwner() {
        CollateralLoanResponse loan = mock(CollateralLoanResponse.class);

        when(loanClient.getByLoanId(1L, "Bearer demo-token"))
                .thenReturn(loan);
        when(loan.getBorrowerId()).thenReturn(20L);

        assertDoesNotThrow(() ->
                collateralService.assertLoanOwnedByBorrower(
                        1L, 20L, "Bearer demo-token"
                )
        );
    }

    @Test
    void assertLoanOwnedByBorrowerRejectsDifferentBorrower() {
        CollateralLoanResponse loan = mock(CollateralLoanResponse.class);

        when(loanClient.getByLoanId(1L, "Bearer demo-token"))
                .thenReturn(loan);
        when(loan.getBorrowerId()).thenReturn(30L);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> collateralService.assertLoanOwnedByBorrower(
                        1L, 20L, "Bearer demo-token"
                )
        );

        assertEquals(403, exception.getStatusCode().value());
    }
    @Test
    void getAllRequestsReturnsAllRequests() {
        CollateralReleaseRequests request =
                new CollateralReleaseRequests();

        when(collateralReleaseRepository.findAll())
                .thenReturn(List.of(request));

        List<CollateralReleaseRequests> result =
                releaseService.getAllRequests();

        assertEquals(1, result.size());
        assertSame(request, result.get(0));
        verify(collateralReleaseRepository).findAll();
    }

    @Test
    void getRequestByIdReturnsRequestWhenFound() {
        CollateralReleaseRequests request =
                new CollateralReleaseRequests();

        when(collateralReleaseRepository.findById(1L))
                .thenReturn(Optional.of(request));

        CollateralReleaseRequests result =
                releaseService.getRequestById(1L);

        assertSame(request, result);
    }

    @Test
    void getRequestByIdThrowsNotFoundWhenMissing() {
        when(collateralReleaseRepository.findById(999L))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> releaseService.getRequestById(999L)
        );

        assertEquals(404, exception.getStatusCode().value());
    }

    @Test
    void getRequestsByLoanIdReturnsMatchingRequests() {
        CollateralReleaseRequests request =
                new CollateralReleaseRequests();

        when(collateralReleaseRepository.findByLoanAccountId(1L))
                .thenReturn(List.of(request));

        List<CollateralReleaseRequests> result =
                releaseService.getRequestsByLoanId(1L);

        assertEquals(1, result.size());
        assertSame(request, result.get(0));
        verify(collateralReleaseRepository).findByLoanAccountId(1L);
    }

    @Test
    void getRequestsByUserIdReturnsBorrowerRequests() {
        CollateralReleaseRequests request =
                new CollateralReleaseRequests();

        when(collateralReleaseRepository.findByRequestedBy(20L))
                .thenReturn(List.of(request));

        List<CollateralReleaseRequests> result =
                releaseService.getRequestsByUserId(20L);

        assertEquals(1, result.size());
        assertSame(request, result.get(0));
        verify(collateralReleaseRepository).findByRequestedBy(20L);
    }

    @Test
    void getRequestsByStatusReturnsMatchingRequests() {
        CollateralReleaseRequests request =
                new CollateralReleaseRequests();

        when(collateralReleaseRepository.findByStatus("PENDING"))
                .thenReturn(List.of(request));

        List<CollateralReleaseRequests> result =
                releaseService.getRequestsByStatus("PENDING");

        assertEquals(1, result.size());
        assertSame(request, result.get(0));
        verify(collateralReleaseRepository).findByStatus("PENDING");
    }

    @Test
    void getMyRequestsReturnsOnlyRequestsForBorrower() {
        CollateralReleaseRequests request =
                new CollateralReleaseRequests();

        when(collateralReleaseRepository.findByRequestedBy(20L))
                .thenReturn(List.of(request));

        List<CollateralReleaseRequests> result =
                releaseService.getMyRequests(20L);

        assertEquals(1, result.size());
        assertSame(request, result.get(0));
    }

    @Test
    void searchRequestsFiltersByLoanIdAndStatus() {
        CollateralReleaseRequests matching =
                mock(CollateralReleaseRequests.class);
        CollateralReleaseRequests differentLoan =
                mock(CollateralReleaseRequests.class);
        CollateralReleaseRequests differentStatus =
                mock(CollateralReleaseRequests.class);

        when(matching.getLoanAccountId()).thenReturn(1L);
        when(matching.getStatus()).thenReturn("PENDING");

        when(differentLoan.getLoanAccountId()).thenReturn(2L);
        // Rejected by the loan-ID filter; its status is never read.

        when(differentStatus.getLoanAccountId()).thenReturn(1L);
        when(differentStatus.getStatus()).thenReturn("REJECTED");

        when(collateralReleaseRepository.findAll()).thenReturn(
                List.of(matching, differentLoan, differentStatus)
        );

        List<CollateralReleaseRequests> result =
                releaseService.searchRequests(1L, "PENDING");

        assertEquals(1, result.size());
        assertSame(matching, result.get(0));
    }
}
