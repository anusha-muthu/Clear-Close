package com.clearclose.exit.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.clearclose.exit.dto.SettlementQuoteDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.web.server.ResponseStatusException;

/** Pure unit tests: no Spring context, database, Kafka, HTTP calls or real clock. */
@ExtendWith(MockitoExtension.class)
class SettlementQuoteServiceTest {
    private static final long BORROWER_ID = 1101L;
    private static final long REQUEST_ID = 61L;
    private static final long LOAN_ID = 41L;
    private static final long QUOTE_ID = 101L;
    private static final LocalDateTime DISBURSED_AT = LocalDateTime.of(2026, 10, 23, 10, 0);

    @Mock private JdbcTemplate jdbc;
    private SettlementQuoteService service;

    @BeforeEach
    void setUp() {
        service = new SettlementQuoteService(jdbc);
    }

    @ParameterizedTest(name = "principal={0}, rate={1}%, seconds={2}: interest={3}, total={4}")
    @CsvSource({
        "100000.00, 12, 86400, 32.88, 100532.88",
        "100000.00, 12, 43200, 16.44, 100516.44",
        "100000.00, 12,    60,  0.02, 100500.02",
        "100000.00, 12,     1,  0.00, 100500.00",
        "100000.00, 12,     0,  0.00, 100500.00",
        "400000.00, 12, 86400, 131.51, 400631.51",
        "100000.00,  0, 86400,  0.00, 100500.00",
        // Exact half-paisa boundary: 49 seconds = .0049, 50 seconds = .0050.
        "315360.00,  1,    49,  0.00, 315860.00",
        "315360.00,  1,    50,  0.01, 315860.01"
    })
    void calculatesProportionateInterestAndPersistsCorrectTotal(
            BigDecimal outstanding, BigDecimal annualRate, long elapsedSeconds,
            BigDecimal expectedInterest, BigDecimal expectedTotal) throws Exception {
        arrangeLoan(outstanding, annualRate, elapsedSeconds, new BigDecimal("500.00"));
        arrangeExistingQuotes(List.of());
        arrangeQuotePersistence();

        SettlementQuoteDto quote = service.createQuote(BORROWER_ID, REQUEST_ID);

        assertAll(
            () -> assertEquals(outstanding, quote.principalAmount()),
            () -> assertEquals(expectedInterest, quote.interestAmount()),
            () -> assertEquals(new BigDecimal("500.00"), quote.chargesAmount()),
            () -> assertEquals(new BigDecimal("0.00"), quote.taxAmount()),
            () -> assertEquals(expectedTotal, quote.totalAmount()),
            () -> assertEquals(Long.valueOf(QUOTE_ID), quote.quoteId()),
            () -> assertEquals("AVAILABLE", quote.quoteStatus())
        );
        verify(jdbc).update(any(PreparedStatementCreator.class), any(KeyHolder.class));
    }

    @ParameterizedTest(name = "rule fee={0}: quote total={1}")
    @CsvSource({
        "0,       100032.88",
        "125.50,  100158.38",
        "500.00,  100532.88",
        "700.00,  100732.88"
    })
    void usesFeeReturnedByLinkedProductRuleRatherThanHardcodedAmount(
            BigDecimal ruleFee, BigDecimal expectedTotal) throws Exception {
        arrangeLoan(new BigDecimal("100000.00"), new BigDecimal("12"), 86400, ruleFee);
        arrangeExistingQuotes(List.of());
        arrangeQuotePersistence();

        SettlementQuoteDto quote = service.createQuote(BORROWER_ID, REQUEST_ID);

        assertAll(
            () -> assertEquals(ruleFee.setScale(2), quote.chargesAmount()),
            () -> assertEquals(new BigDecimal("32.88"), quote.interestAmount()),
            () -> assertEquals(new BigDecimal("0.00"), quote.taxAmount()),
            () -> assertEquals(expectedTotal, quote.totalAmount())
        );
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"-0.01", "0.001", "10000000000000.00"})
    void rejectsInvalidRuleFeeWithoutSavingQuote(String rawFee) throws Exception {
        BigDecimal ruleFee = rawFee == null ? null : new BigDecimal(rawFee);
        arrangeLoan(new BigDecimal("100000.00"), new BigDecimal("12"), 86400, ruleFee);
        arrangeExistingQuotes(List.of());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.createQuote(BORROWER_ID, REQUEST_ID));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(jdbc, never()).update(any(PreparedStatementCreator.class), any(KeyHolder.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"AVAILABLE", "ACCEPTED", "PAID"})
    void preservesSavedQuoteEvenWhenProductRuleFeeChanges(String status) throws Exception {
        arrangeLoan(new BigDecimal("100000.00"), new BigDecimal("12"), 86400, new BigDecimal("700.00"));
        SettlementQuoteDto saved = new SettlementQuoteDto(
            QUOTE_ID, LOAN_ID, REQUEST_ID, "COOLING_OFF",
            new BigDecimal("100000.00"), new BigDecimal("32.88"),
            new BigDecimal("125.50"), new BigDecimal("0.00"), new BigDecimal("100158.38"),
            status, DISBURSED_AT.plusDays(2), null, DISBURSED_AT.plusDays(1), false);
        arrangeExistingQuotes(List.of(saved));

        SettlementQuoteDto result = service.createQuote(BORROWER_ID, REQUEST_ID);

        assertSame(saved, result);
        assertEquals(new BigDecimal("125.50"), result.chargesAmount());
        assertEquals(new BigDecimal("100158.38"), result.totalAmount());
        verify(jdbc, never()).update(any(PreparedStatementCreator.class), any(KeyHolder.class));
    }

    private void arrangeLoan(BigDecimal outstanding, BigDecimal annualRate,
            long elapsedSeconds, BigDecimal ruleFee) throws Exception {
        ResultSet row = mock(ResultSet.class);
        when(row.getLong("EXIT_REQUEST_ID")).thenReturn(REQUEST_ID);
        when(row.getLong("LOAN_ID")).thenReturn(LOAN_ID);
        when(row.getString("REQUEST_STATUS")).thenReturn("APPROVED");
        when(row.getString("LOAN_STATUS")).thenReturn("ACTIVE");
        when(row.getBigDecimal("OUTSTANDING_AMOUNT")).thenReturn(outstanding);
        when(row.getBigDecimal("INTEREST_RATE")).thenReturn(annualRate);
        when(row.getTimestamp("DISBURSEMENT_DATE")).thenReturn(Timestamp.valueOf(DISBURSED_AT));
        when(row.getInt("COOLING_OFF_DAYS")).thenReturn(5);
        when(row.getBigDecimal("COOLING_OFF_PROCESSING_FEE")).thenReturn(ruleFee);
        when(row.getTimestamp("REQUEST_EXPIRY")).thenReturn(Timestamp.valueOf(DISBURSED_AT.plusDays(5)));
        when(row.getTimestamp("CURRENT_DB_TIME"))
            .thenReturn(Timestamp.valueOf(DISBURSED_AT.plusSeconds(elapsedSeconds)));

        // Exercise the production row mapper, without reflecting into its private record.
        doAnswer(invocation -> {
            RowMapper<?> mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(row, 0));
        }).when(jdbc).query(contains("FROM COOLING_OFF_REQUEST c"),
            org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), eq(REQUEST_ID), eq(BORROWER_ID));
    }

    private void arrangeExistingQuotes(List<SettlementQuoteDto> quotes) {
        doReturn(quotes).when(jdbc).query(contains("AND q.EXIT_REQUEST_ID = ?"),
            org.mockito.ArgumentMatchers.<RowMapper<SettlementQuoteDto>>any(), eq(BORROWER_ID), eq(REQUEST_ID));
    }

    /**
     * Tiny mock persistence adapter: captures values bound by the REAL insert callback
     * and returns those same values via the REAL quote row mapper. It performs no
     * financial calculations and never supplies a precomputed expected total.
     */
    private void arrangeQuotePersistence() throws Exception {
        Map<Integer, Object> bound = new HashMap<>();
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString(), any(String[].class))).thenReturn(statement);
        doAnswer(invocation -> {
            bound.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(statement).setLong(anyInt(), anyLong());
        doAnswer(invocation -> {
            bound.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(statement).setBigDecimal(anyInt(), any(BigDecimal.class));
        doAnswer(invocation -> {
            bound.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(statement).setTimestamp(anyInt(), any(Timestamp.class));

        doAnswer(invocation -> {
            PreparedStatementCreator creator = invocation.getArgument(0);
            creator.createPreparedStatement(connection);
            KeyHolder keys = invocation.getArgument(1);
            keys.getKeyList().add(Map.of("QUOTE_ID", QUOTE_ID));
            return 1;
        }).when(jdbc).update(any(PreparedStatementCreator.class), any(KeyHolder.class));

        doAnswer(invocation -> {
            ResultSet row = mock(ResultSet.class);
            when(row.getLong("QUOTE_ID")).thenReturn(QUOTE_ID);
            when(row.getLong("LOAN_ID")).thenReturn((Long) bound.get(1));
            when(row.getLong("EXIT_REQUEST_ID")).thenReturn((Long) bound.get(2));
            when(row.getString("QUOTE_TYPE")).thenReturn("COOLING_OFF");
            when(row.getBigDecimal("PRINCIPAL_AMOUNT")).thenReturn((BigDecimal) bound.get(3));
            when(row.getBigDecimal("INTEREST_AMOUNT")).thenReturn((BigDecimal) bound.get(4));
            when(row.getBigDecimal("CHARGES_AMOUNT")).thenReturn((BigDecimal) bound.get(5));
            when(row.getBigDecimal("TAX_AMOUNT")).thenReturn((BigDecimal) bound.get(6));
            when(row.getBigDecimal("TOTAL_AMOUNT")).thenReturn((BigDecimal) bound.get(7));
            when(row.getString("QUOTE_STATUS")).thenReturn("AVAILABLE");
            when(row.getTimestamp("VALID_UNTIL")).thenReturn((Timestamp) bound.get(8));
            // Null ACCEPTED_AT/CREATED_AT and zero IS_EXPIRED use Mockito defaults.
            RowMapper<?> mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(row, 0));
        }).when(jdbc).query(contains("AND q.QUOTE_ID = ?"),
            org.mockito.ArgumentMatchers.<RowMapper<SettlementQuoteDto>>any(), eq(BORROWER_ID), eq(QUOTE_ID));
    }
}
