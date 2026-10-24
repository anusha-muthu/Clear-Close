package com.clearclose.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class CollateralClientTests {
    @Test
    void availableNocAllowsReleaseEvenWhenReplacementFailed() {
        assertTrue(checkNoc("[{\"status\":\"FAILED\"},{\"status\":\"AVAILABLE\"}]"));
    }

    @Test
    void failedRevokedOrPendingNocsDoNotAllowRelease() {
        assertFalse(checkNoc("[{\"status\":\"FAILED\"},{\"status\":\"REVOKED\"},{\"status\":\"GENERATION_PENDING\"}]"));
        assertFalse(checkNoc("[]"));
    }

    private boolean checkNoc(String response) {
        var builder = RestClient.builder().baseUrl("http://noc.test");
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new NocClient("http://noc.test");
        ReflectionTestUtils.setField(client, "client", builder.build());
        server.expect(requestTo("http://noc.test/api/noc/loan/4104"))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        boolean available = client.hasAvailableNoc(4104L, "test-token");
        server.verify();
        return available;
    }

    @Test
    void loanLookupExplainsAuthenticationAccessAndMissingLoanSeparately() {
        assertLoanError(HttpStatus.UNAUTHORIZED, "Please sign in again");
        assertLoanError(HttpStatus.FORBIDDEN, "do not have access");
        assertLoanError(HttpStatus.NOT_FOUND, "Check the loan account ID");
    }

    private void assertLoanError(HttpStatus status, String message) {
        var builder = RestClient.builder().baseUrl("http://loans.test");
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new LoanAccountClient(builder.build());
        server.expect(requestTo("http://loans.test/internal/loanAccounts/loan/4104"))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andRespond(withStatus(status));
        var error = assertThrows(ResponseStatusException.class,
                () -> client.getByLoanId(4104L, "test-token"));
        assertEquals(status, error.getStatusCode());
        assertTrue(error.getReason().contains(message));
        server.verify();
    }
}
