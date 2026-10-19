
package com.clearclose.exit.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import com.clearclose.exit.dto.ExitRequestDto.CreateRequest;
import com.clearclose.exit.dto.ExitRequestDto.RequestResponse;
import com.clearclose.exit.service.ExitRequestService;


@RestController
@RequestMapping("/api/cooling-off/requests")
public class ExitRequestController {

    private final ExitRequestService exitRequestService;

    public ExitRequestController(
        ExitRequestService exitRequestService
    ) {
        this.exitRequestService = exitRequestService;
    }

    // ---------------------------------------
    // CREATE EXIT REQUEST
    // ---------------------------------------

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RequestResponse createRequest(
        @RequestBody CreateRequest request,
        @AuthenticationPrincipal Jwt jwt
    ) {

        Long borrowerId =
            ((Number) jwt.getClaim("userId")).longValue();

        return exitRequestService.createRequest(
            borrowerId,
            request
        );
    }

    // ---------------------------------------
    // GET ALL MY REQUESTS
    // ---------------------------------------

    @GetMapping
    public List<RequestResponse> getMyRequests(
        @AuthenticationPrincipal Jwt jwt
    ) {

        Long borrowerId =
            ((Number) jwt.getClaim("userId")).longValue();

        return exitRequestService.getMyRequests(
            borrowerId
        );
    }

    // ---------------------------------------
    // GET SPECIFIC REQUEST
    // ---------------------------------------

    @GetMapping("/{requestId}")
    public RequestResponse getRequestById(
        @PathVariable Long requestId,
        @AuthenticationPrincipal Jwt jwt
    ) {

        Long borrowerId =
            ((Number) jwt.getClaim("userId")).longValue();

        return exitRequestService.getRequestById(
            borrowerId,
            requestId
        );
    }
}
