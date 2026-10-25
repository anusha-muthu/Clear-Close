package com.ofss.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

/** Counts Monday through Friday, excluding configured holidays. */
public final class GrievanceWorkingDays {
    private GrievanceWorkingDays() {}

    public static LocalDateTime dueAt(LocalDateTime start, int workingDays) {
        return dueAt(start, workingDays, Set.of());
    }

    public static LocalDateTime dueAt(
            LocalDateTime start, int workingDays, Set<LocalDate> holidays) {
        if (start == null) return null;
        if (workingDays < 0) throw new IllegalArgumentException("Days cannot be negative");
        Set<LocalDate> excluded = holidays == null ? Set.of() : holidays;
        LocalDateTime deadline = start;
        int counted = 0;
        while (counted < workingDays) {
            deadline = deadline.plusDays(1);
            DayOfWeek day = deadline.getDayOfWeek();
            if (day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY
                    && !excluded.contains(deadline.toLocalDate())) counted++;
        }
        return deadline;
    }

    public static boolean isOverdue(LocalDateTime start, LocalDateTime now, int workingDays) {
        return isOverdue(start, now, workingDays, Set.of());
    }

    public static boolean isOverdue(
            LocalDateTime start, LocalDateTime now, int workingDays,
            Set<LocalDate> holidays) {
        LocalDateTime deadline = dueAt(start, workingDays, holidays);
        return deadline != null && now != null && now.isAfter(deadline);
    }
}
