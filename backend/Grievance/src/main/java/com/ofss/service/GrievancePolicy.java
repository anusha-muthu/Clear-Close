package com.ofss.service;

import java.util.Locale;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

public final class GrievancePolicy {
    private GrievancePolicy() {}

    public static final Set<String> OFFICER_ROLES = Set.of(
        "LOAN_CLOSURE_OFFICER", "GRIEVANCE_OFFICER", "COLLATERAL_OFFICER","SENIOR_GRIEVANCE_OFFICER"
    );

    public static final Set<String> STATUSES = Set.of(
        "OPEN", "IN_PROGRESS","ESCALATED", "CLOSED"
    );

    private static final Set<String> CATEGORIES = Set.of("DELAY", "SETTLEMENT", "SYSTEM", "OTHER");
    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
        "OPEN", Set.of("IN_PROGRESS", "ESCALATED","CLOSED"),
        "IN_PROGRESS", Set.of( "ESCALATED","CLOSED"),
        "ESCALATED", Set.of("IN_PROGRESS", "CLOSED"),
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

    public static boolean isOverdue(
            String status, java.time.LocalDateTime createdAt,
            java.time.LocalDateTime now) {
        String value = status == null ? "" : normalize(status);
        return java.util.List.of("OPEN", "IN_PROGRESS", "MORE_INFO_REQUIRED").contains(value)
            && GrievanceWorkingDays.isOverdue(createdAt, now, 7, GrievanceCalendar.HOLIDAYS);
    }

    public static boolean canTransition(String oldStatus, String newStatus) {
        if (oldStatus == null || newStatus == null) {
            return false;
        }

        String from = normalize(oldStatus);
        String to = normalize(newStatus);

        return switch (from) {
            case "OPEN" ->
                "IN_PROGRESS".equals(to) || "ESCALATED".equals(to) || "CLOSED".equals(to);

            case "IN_PROGRESS" ->
                "ESCALATED".equals(to) || "CLOSED".equals(to);

            case "ESCALATED" ->
                "IN_PROGRESS".equals(to) ||"CLOSED".equals(to);

            default -> false;
        };
    }
}
