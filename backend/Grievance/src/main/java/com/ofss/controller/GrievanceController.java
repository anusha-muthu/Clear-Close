package com.ofss.controller;

import com.ofss.entity.Grievances;
import com.ofss.entity.GrievancesHistory;
import com.ofss.service.GrievanceService;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

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
    public Grievances submit(
            @RequestBody Grievances grievance,
            @AuthenticationPrincipal Jwt jwt) {
        grievance.setBorrowerId(requireUserId(jwt));
        return grievanceService.submitGrievance(
                grievance,
                requireUserId(jwt),
                jwt.getClaimAsString("accountType"));
    }

    //-------------- View all grievances
    @GetMapping
    public List<Grievances> getAll() {
        return grievanceService.getAllGrievances();
    }

    //--------------- View one grievance
    @GetMapping("/{id}")
    public Grievances getOne(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        Grievances grievance = grievanceService.getGrievance(id);
        requireGrievanceAccess(grievance, jwt);
        return grievance;
    }

    //---------------- View borrower grievances
    @GetMapping("/borrower/{borrowerId}")
    public List<Grievances> getByBorrower(
            @PathVariable Long borrowerId,
            @AuthenticationPrincipal Jwt jwt) {
        Long authenticatedBorrowerId = requireUserId(jwt);
        if (!authenticatedBorrowerId.equals(borrowerId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You can only view your own grievances");
        }
        return grievanceService.getByBorrower(authenticatedBorrowerId);
    }

    //---------- View officer's assigned grievances
    @GetMapping("/officer/{officerId}")
    public List<Grievances> getByOfficer(
            @PathVariable Long officerId,
            @AuthenticationPrincipal Jwt jwt) {
        Long authenticatedOfficerId = requireUserId(jwt);
        if (!isAdmin(jwt) && !authenticatedOfficerId.equals(officerId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You can only view grievances assigned to you");
        }
        return grievanceService.getByOfficer(
                isAdmin(jwt) ? officerId : authenticatedOfficerId);
    }

    //---------------- Assign grievance
    @PatchMapping("/{id}/assign")
    public Grievances assign(
            @PathVariable Long id,
            @RequestBody Map<String, Long> body,
            @AuthenticationPrincipal Jwt jwt) {

        return grievanceService.assignGrievance(
            id,
            body.get("officerId"),
            requireUserId(jwt),
            jwt.getClaimAsString("accountType")
        );
    }

    //-------------------- Update grievance status
    @PatchMapping("/{id}/status")
    public Grievances updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal Jwt jwt) {

        return grievanceService.updateStatus(
            id,
            (String) body.get("status"),
            (String) body.get("remarks"),
            requireUserId(jwt),
            jwt.getClaimAsString("accountType")
        );
    }

    // ----------View grievance history
    @GetMapping("/{id}/history")
    public List<GrievancesHistory> getHistory(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        requireGrievanceAccess(grievanceService.getGrievance(id), jwt);
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

    private Long requireUserId(Jwt jwt) {
        Number userId = jwt.getClaim("userId");
        if (userId == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "User ID is missing from the authenticated token");
        }
        return userId.longValue();
    }

    private void requireGrievanceAccess(Grievances grievance, Jwt jwt) {
        if (!isOfficer(jwt)
                && !requireUserId(jwt).equals(grievance.getBorrowerId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You can only view your own grievances");
        }
    }

    private boolean isOfficer(Jwt jwt) {
        return "OFFICER".equals(jwt.getClaimAsString("accountType"))
                && ("GRIEVANCE_OFFICER".equals(jwt.getClaimAsString("role"))
                    || isAdmin(jwt));
    }

    private boolean isAdmin(Jwt jwt) {
        return "OFFICER".equals(jwt.getClaimAsString("accountType"))
                && "ADMIN".equals(jwt.getClaimAsString("role"));
    }
}
