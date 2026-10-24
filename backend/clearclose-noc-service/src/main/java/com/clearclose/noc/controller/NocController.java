
package com.clearclose.noc.controller;

import com.clearclose.noc.dto.NocEligibilityResponse;
import com.clearclose.noc.dto.NocResponse;
import com.clearclose.noc.entity.NocAudit;
import com.clearclose.noc.service.NocService;
import com.clearclose.noc.security.NocAccess;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/noc")
public class NocController {

    private final NocService service;
    private final NocAccess access;

    public NocController(NocService service, NocAccess access) {
        this.service = service;
        this.access = access;
    }

    @GetMapping("/issued")
    public List<NocResponse> issued(@AuthenticationPrincipal Jwt jwt) {
        return service.recentCertificates(access.actor(jwt));
    }

    // Check NOC eligibility
    @GetMapping("/eligibility/{loanId}")
    public NocEligibilityResponse eligibility(
            @PathVariable("loanId") Long loanId, @AuthenticationPrincipal Jwt jwt) {

        return service.eligibility(loanId, access.actor(jwt));
    }

    // Generate a new certificate
@PostMapping("/generate/{loanId}")
public ResponseEntity<NocResponse> generate(
        @PathVariable("loanId") Long loanId,
        @AuthenticationPrincipal Jwt jwt) {

    NocResponse response =
            service.generate(loanId, access.actor(jwt));

    return ResponseEntity
            .status(
                "AVAILABLE".equals(response.status())
                    ? HttpStatus.CREATED
                    : HttpStatus.INTERNAL_SERVER_ERROR
            )
            .body(response);
}


    // Reissue certificate

@PostMapping("/reissue/{loanId}")
public ResponseEntity<NocResponse> reissue(
        @PathVariable("loanId") Long loanId,
        @AuthenticationPrincipal Jwt jwt) {

    NocResponse response =
            service.reissue(loanId, access.actor(jwt));

    return ResponseEntity
            .status(
                "AVAILABLE".equals(response.status())
                    ? HttpStatus.CREATED
                    : HttpStatus.INTERNAL_SERVER_ERROR
            )
            .body(response);
}


    // Retry failed generation
    @PostMapping("/{nocId}/retry")
    public NocResponse retry(
            @PathVariable("nocId") Long nocId,
            @AuthenticationPrincipal Jwt jwt) {

        return service.retry(nocId, access.actor(jwt));
    }

    // Get all NOCs belonging to a loan
    @GetMapping("/loan/{loanId}")
    public List<NocResponse> getByLoan(
            @PathVariable("loanId") Long loanId, @AuthenticationPrincipal Jwt jwt) {

        return service.getByLoan(loanId, access.actor(jwt));
    }

    // Get NOC by ID
    @GetMapping("/{nocId}")
    public NocResponse getById(
            @PathVariable("nocId") Long nocId, @AuthenticationPrincipal Jwt jwt) {

        return service.getById(nocId, access.actor(jwt));
    }

    // Verify certificate number
    @GetMapping("/verify/{certificateNumber}")
    public NocResponse verify(
            @PathVariable("certificateNumber") String certificateNumber, @AuthenticationPrincipal Jwt jwt) {

        return service.verify(certificateNumber, access.actor(jwt));
    }

    // Download PDF
    @GetMapping(
        value = "/{nocId}/download",
        produces = MediaType.APPLICATION_PDF_VALUE
    )
    public ResponseEntity<byte[]> download(
            @PathVariable("nocId") Long nocId,
            @AuthenticationPrincipal Jwt jwt) {

        byte[] pdf = service.download(nocId, access.actor(jwt));

        String filename = "NOC-" + nocId + ".pdf";

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(
                    HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + filename + "\"")
                .contentLength(pdf.length)
                .body(pdf);
    }

    // View certificate audit history
    @GetMapping("/{nocId}/history")
    public List<NocAudit> history(
            @PathVariable("nocId") Long nocId, @AuthenticationPrincipal Jwt jwt) {

        return service.history(nocId, access.actor(jwt));
    }
}
