package com.ofss.controller;

import com.ofss.entity.Grievances;
import com.ofss.entity.GrievancesHistory;
import com.ofss.service.GrievanceService;

import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/grievances")
@CrossOrigin(origins = "http://localhost:5173")
public class GrievanceController {

    private final GrievanceService grievanceService;

    public GrievanceController(GrievanceService grievanceService) {
        this.grievanceService = grievanceService;
    }

    //---------------- Submit grievance
    @PostMapping
    public Grievances submit(@RequestBody Grievances grievance) {
        return grievanceService.submitGrievance(grievance);
    }

    //-------------- View all grievances
    @GetMapping
    public List<Grievances> getAll() {
        return grievanceService.getAllGrievances();
    }

    //--------------- View one grievance
    @GetMapping("/{id}")
    public Grievances getOne(@PathVariable Long id) {
        return grievanceService.getGrievance(id);
    }

    //---------------- View borrower grievances
    @GetMapping("/borrower/{borrowerId}")
    public List<Grievances> getByBorrower(
            @PathVariable Long borrowerId) {
        return grievanceService.getByBorrower(borrowerId);
    }

    //---------- View officer's assigned grievances
    @GetMapping("/officer/{officerId}")
    public List<Grievances> getByOfficer(
            @PathVariable Long officerId) {
        return grievanceService.getByOfficer(officerId);
    }

    //---------------- Assign grievance
    @PatchMapping("/{id}/assign")
    public Grievances assign(
            @PathVariable Long id,
            @RequestBody Map<String, Long> body) {

        return grievanceService.assignGrievance(
            id,
            body.get("officerId"),
            body.get("actionBy")
        );
    }

    //-------------------- Update grievance status
    @PatchMapping("/{id}/status")
    public Grievances updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {

        return grievanceService.updateStatus(
            id,
            (String) body.get("status"),
            (String) body.get("remarks"),
            Long.valueOf(body.get("actionBy").toString())
        );
    }

    // ----------View grievance history
    @GetMapping("/{id}/history")
    public List<GrievancesHistory> getHistory(
            @PathVariable Long id) {
        return grievanceService.getHistory(id);
    }
    
    //---------------Filter grievances by status
    @GetMapping("/status/{status}")
    public List<Grievances> getByStatus(@PathVariable String status) {
        return grievanceService.getByStatus(status);
    }


    // --------------Find grievances related to the loan id 
    @GetMapping("/loan/{loanId}")
    public List<Grievances> getByLoanId(
            @PathVariable Long loanId) {

        return grievanceService.getByLoanId(loanId);
    }

    // -------------------Find grievances exceeding the window
    @GetMapping("/overdue")
    public List<Grievances> getOverdueGrievances() {
        return grievanceService.getOverdueGrievances();
    }
}