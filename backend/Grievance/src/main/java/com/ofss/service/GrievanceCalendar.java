package com.ofss.service;

import java.time.LocalDate;
import java.util.Set;

/** Replace the example dates with the organization's approved holiday calendar. */
public final class GrievanceCalendar {
    private GrievanceCalendar() {}

    public static final Set<LocalDate> HOLIDAYS = Set.of(
            // Example only. Add the approved dates for each year.
            LocalDate.of(2026, 1, 26),
            LocalDate.of(2026, 8, 15),
            LocalDate.of(2026, 10, 2)
    );
}
