package com.ofss.controller;

import com.ofss.entity.Grievances;
import com.ofss.entity.GrievancesHistory;
import com.ofss.service.GrievanceService;
import com.ofss.service.GrievanceService.SeniorOverdueNotice;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Set;

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
        requireBorrower(jwt);
        grievance.setBorrowerId(requireUserId(jwt));
        return grievanceService.submitGrievance(
                grievance,
                requireUserId(jwt),
                jwt.getClaimAsString("accountType"));
    }

    //-------------- View all grievances
    @GetMapping
    public List<Grievances> getAll(@AuthenticationPrincipal Jwt jwt) {
        requireSenior(jwt);
        return grievanceService.getSeniorQueue(
            requireUserId(jwt), jwt.getClaimAsString("accountType"));
    }


    //--------------- View one grievance
    @GetMapping("/{id}")
    public Grievances getOne(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal Jwt jwt) {
        Grievances grievance = grievanceService.getGrievance(id);
        requireGrievanceAccess(grievance, jwt);
        return grievance;
    }
    
    @GetMapping("/senior/overdue-notifications")
    public List<SeniorOverdueNotice> getSeniorOverdueNotifications(
            @AuthenticationPrincipal Jwt jwt) {

        Number claim = jwt.getClaim("officerId");
        if (claim == null) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Officer ID is missing from the token"
            );
        }

        Long officerId = claim.longValue();
        return grievanceService.getSeniorOverdueNotifications(officerId);
    }

    //---------------- View borrower grievances
    @GetMapping("/borrower/{borrowerId}")
    public List<Grievances> getByBorrower(
            @PathVariable("borrowerId") Long borrowerId,
            @AuthenticationPrincipal Jwt jwt) {
        requireBorrower(jwt);
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
            @PathVariable("officerId") Long officerId,
            @AuthenticationPrincipal Jwt jwt) {
        requireOfficer(jwt);
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
            @PathVariable("id") Long id,
            @RequestBody Map<String, Long> body,
            @AuthenticationPrincipal Jwt jwt) {
        requireSenior(jwt);
        Long targetId = body.get("officerId");
        if (targetId == null || targetId <= 0) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "A valid officerId is required");
        }
        return grievanceService.assignGrievance(
            id, targetId, requireUserId(jwt), jwt.getClaimAsString("accountType"));
    }


    //-------------------- Update grievance status
    @PatchMapping("/{id}/status")
    public Grievances updateStatus(
            @PathVariable("id") Long id,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal Jwt jwt) {

        requireAssignedOfficer(grievanceService.getGrievance(id), jwt);
        Object requestedStatus = body.get("status");
        Object remarks = body.get("remarks");
        if (!(requestedStatus instanceof String) || !(remarks instanceof String)
                || ((String) remarks).isBlank() || ((String) remarks).length() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status and 1-1000 character remarks are required");
        }
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
            @PathVariable("id") Long id,
            @AuthenticationPrincipal Jwt jwt) {
        requireGrievanceAccess(grievanceService.getGrievance(id), jwt);
        return grievanceService.getHistory(id);
    }
    
//    //---------------Filter grievances by status
//    @GetMapping("/status/{status}")
//    public List<Grievances> getByStatus(@PathVariable("status") String status, @AuthenticationPrincipal Jwt jwt) {
//        requireAdmin(jwt);
//        return grievanceService.getByStatus(status);
//    }
//
//
//    // --------------Find grievances related to the loan id 
//    @GetMapping("/loan/{loanId}")
//    public List<Grievances> getByLoanId(
//            @PathVariable("loanId") Long loanId, @AuthenticationPrincipal Jwt jwt) {
//        requireAdmin(jwt);
//        return grievanceService.getByLoanId(loanId);
//    }

    // -------------------Find grievances exceeding the window
    @GetMapping("/overdue")
    public List<Grievances> getOverdueGrievances(@AuthenticationPrincipal Jwt jwt) {
        requireAdmin(jwt);
        return grievanceService.getOverdueGrievances();
    }

    private static final Set<String> OFFICER_ROLES = Set.of(
            "LOAN_CLOSURE_OFFICER", "GRIEVANCE_OFFICER", "COLLATERAL_OFFICER", "ADMIN","SENIOR_GRIEVANCE_OFFICER");

    private Long requireUserId(Jwt jwt) {
        if (jwt == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
        try {
            Long id = Long.valueOf(String.valueOf((Object) jwt.getClaim("userId")));
            if (id <= 0) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token user ID");
        }
    }

    private void requireBorrower(Jwt jwt) {
        requireUserId(jwt);
        if (!"BORROWER".equals(jwt.getClaimAsString("accountType"))
                || !"BORROWER".equals(jwt.getClaimAsString("role"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "A borrower account is required");
        }
    }

    private void requireOfficer(Jwt jwt) {
        requireUserId(jwt);
        if (!isOfficer(jwt)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "An officer account is required");
        }
    }

    private void requireAdmin(Jwt jwt) {
        requireOfficer(jwt);
        if (!isAdmin(jwt)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator access is required");
        }
    }

    private void requireAssignedOfficer(Grievances grievance, Jwt jwt) {
        requireOfficer(jwt);
        // Assumes Grievances exposes ASSIGNED_TO as Long getAssignedTo().
        if (!requireUserId(jwt).equals(grievance.getAssignedTo())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the assigned officer can update this grievance");
        }
    }

    private void requireGrievanceAccess(Grievances grievance, Jwt jwt) {
        Long actorId = requireUserId(jwt);
        if (isAdmin(jwt)) return;
        if (isOfficer(jwt) && actorId.equals(grievance.getAssignedTo())) return;
        if ("BORROWER".equals(jwt.getClaimAsString("accountType"))
                && "BORROWER".equals(jwt.getClaimAsString("role"))
                && actorId.equals(grievance.getBorrowerId())) return;
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "You may view only your own or assigned grievances");
    }

    private boolean isOfficer(Jwt jwt) {
        if (jwt == null || !"OFFICER".equals(jwt.getClaimAsString("accountType"))) return false;
        String role = jwt.getClaimAsString("role");
        return role != null && OFFICER_ROLES.contains(role);
    }

    private boolean isAdmin(Jwt jwt) {
        return jwt != null && "OFFICER".equals(jwt.getClaimAsString("accountType"))
                && "ADMIN".equals(jwt.getClaimAsString("role"));
    }
    

	private void requireSenior(Jwt jwt) {
	    requireOfficer(jwt);
	    if (!"SENIOR_GRIEVANCE_OFFICER".equals(jwt.getClaimAsString("role"))) {
	        throw new ResponseStatusException(
	            HttpStatus.FORBIDDEN, "A senior grievance officer is required");
	    }
}

}

