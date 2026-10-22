
package com.clearclose.noc.controller;

import com.clearclose.noc.dto.NocEligibilityResponse;
import com.clearclose.noc.dto.NocResponse;
import com.clearclose.noc.entity.NocAudit;
import com.clearclose.noc.service.NocService;

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

    public NocController(NocService service) {
        this.service = service;
    }

    // Check NOC eligibility
    @GetMapping("/eligibility/{loanId}")
    public NocEligibilityResponse eligibility(
            @PathVariable Long loanId) {

        return service.eligibility(loanId);
    }

    // Generate a new certificate
@PostMapping("/generate/{loanId}")
public ResponseEntity<NocResponse> generate(
        @PathVariable Long loanId,
        @RequestParam Long officerId) {

    NocResponse response =
            service.generate(loanId, officerId);

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
        @PathVariable Long loanId,
        @RequestParam Long officerId) {

    NocResponse response =
            service.reissue(loanId, officerId);

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
            @PathVariable Long nocId,
            @RequestParam Long officerId) {

        return service.retry(nocId, officerId);
    }

    // Get all NOCs belonging to a loan
    @GetMapping("/loan/{loanId}")
    public List<NocResponse> getByLoan(
            @PathVariable Long loanId) {

        return service.getByLoan(loanId);
    }

    // Get NOC by ID
    @GetMapping("/{nocId}")
    public NocResponse getById(
            @PathVariable Long nocId) {

        return service.getById(nocId);
    }

    // Verify certificate number
    @GetMapping("/verify/{certificateNumber}")
    public NocResponse verify(
            @PathVariable String certificateNumber) {

        return service.verify(certificateNumber);
    }

    // Download PDF
    @GetMapping(
        value = "/{nocId}/download",
        produces = MediaType.APPLICATION_PDF_VALUE
    )
    public ResponseEntity<byte[]> download(
            @PathVariable Long nocId,
            @RequestParam Long actorId) {

        byte[] pdf = service.download(nocId, actorId);

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
            @PathVariable Long nocId) {

        return service.history(nocId);
    }
}
