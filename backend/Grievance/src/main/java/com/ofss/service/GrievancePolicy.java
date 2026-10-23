package com.ofss.service;

import java.util.Locale;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

public final class GrievancePolicy {
    private GrievancePolicy() {}

    public static final Set<String> OFFICER_ROLES = Set.of(
        "LOAN_CLOSURE_OFFICER", "GRIEVANCE_OFFICER", "COLLATERAL_OFFICER", "ADMIN","SENIOR_GRIEVANCE_OFFICER"
    );

    public static final Set<String> STATUSES = Set.of(
        "OPEN", "IN_PROGRESS", "MORE_INFO_REQUIRED", "RESOLVED",
        "REJECTED", "ESCALATED", "CLOSED"
    );

    private static final Set<String> CATEGORIES = Set.of("DELAY", "SETTLEMENT", "SYSTEM", "OTHER");
    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
        "OPEN", Set.of("IN_PROGRESS", "ESCALATED"),
        "IN_PROGRESS", Set.of("MORE_INFO_REQUIRED", "RESOLVED", "REJECTED", "ESCALATED"),
        "MORE_INFO_REQUIRED", Set.of("IN_PROGRESS", "ESCALATED"),
        "ESCALATED", Set.of("IN_PROGRESS", "MORE_INFO_REQUIRED", "RESOLVED", "REJECTED"),
        "RESOLVED", Set.of("CLOSED"),
        "REJECTED", Set.of(),
        "CLOSED", Set.of()
    );

    public static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    public static String newCategory(String category) {
        String result = normalize(category);
        if (!CATEGORIES.contains(result)) {
            throw new IllegalArgumentException("Category must be DELAY, SETTLEMENT, SYSTEM or OTHER");
        }
        return result;
    }

    public static String roleForCategory(String category) {
        return switch (normalize(category)) {
            case "DELAY" -> "COLLATERAL_OFFICER";
            case "SETTLEMENT" -> "LOAN_CLOSURE_OFFICER";
            // Includes legacy categories when administrators assign old complaints.
            default -> "GRIEVANCE_OFFICER";
        };
    }

    // Five elapsed days since submission, not five days since the last update.
    public static boolean isOverdue(String status, LocalDateTime createdAt, LocalDateTime now) {
        return Set.of("OPEN", "IN_PROGRESS", "MORE_INFO_REQUIRED").contains(status)
            && createdAt != null && now != null && createdAt.isBefore(now.minusDays(5));
    }

    public static boolean canTransition(String from, String to) {
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }
}
