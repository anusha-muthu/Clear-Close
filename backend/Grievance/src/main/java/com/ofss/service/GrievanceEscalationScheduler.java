package com.ofss.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import com.ofss.service.GrievanceService;
import org.springframework.stereotype.Component;

@Component
public class GrievanceEscalationScheduler {
    private static final Logger log = LoggerFactory.getLogger(GrievanceEscalationScheduler.class);
    private final GrievanceService grievanceService;

    public GrievanceEscalationScheduler(GrievanceService grievanceService) {
        this.grievanceService = grievanceService;
    }

    @Scheduled(fixedDelayString = "${app.grievances.escalation-delay-ms:300000}")
    public void checkOverdueGrievances() {
        int count = grievanceService.escalateOverdueGrievances();
        if (count > 0) log.info("Automatically escalated {} overdue grievances", count);
    }
}
