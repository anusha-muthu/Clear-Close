package com.clearclose.controller;
import jakarta.validation.Valid; 
import com.clearclose.entity.Collateral;
import com.clearclose.service.CollateralService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/collateral")
public class CollateralController {

    private final CollateralService collateralService;

    public CollateralController(CollateralService collateralService) {
        this.collateralService = collateralService;
    }

    @PostMapping
    public ResponseEntity<Collateral> createCollateral(@Valid @RequestBody Collateral collateral) { 
        // @Valid must be explicitly present here!
        return ResponseEntity.ok(collateralService.createCollateral(collateral));
    }


    @GetMapping
    public List<Collateral> getAllCollateral() {
        return collateralService.getAllCollateral();
    }

    @GetMapping("/{id}")
    public Collateral getCollateralById(@PathVariable Long id) {
        return collateralService.getCollateralById(id);
    }

    @GetMapping("/loan/{loanId}")
    public List<Collateral> getCollateralByLoanId(
            @PathVariable Long loanId) {
        return collateralService.getCollateralByLoanId(loanId);
    }

    @PutMapping("/{id}")
    public Collateral updateCollateral(
            @PathVariable Long id,
            @Valid @RequestBody Collateral collateral) {
        return collateralService.updateCollateral(id, collateral);
    }
    
    @GetMapping("/types")
   	public List<String> getCollateralTypes() {
    	return collateralService.getCollateralTypes();
  }

    @GetMapping("/{id}/eligibility")
    public Map<String, Object> checkEligibility(@PathVariable Long id) {
    	return collateralService.checkEligibility(id);
    }
}
