package com.clearclose.controller;

import com.clearclose.dto.BorrowerResponse;
import com.clearclose.entity.Borrowers;
import com.clearclose.repository.BorrowerRepository;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/borrowers")
public class BorrowerController {

	private final BorrowerRepository borrowerRepository;

	public BorrowerController(
		BorrowerRepository borrowerRepository){
		this.borrowerRepository = borrowerRepository;
		}

	@GetMapping("/username/{username}")
	public BorrowerResponse getByUsername(@PathVariable String username) {

		Borrowers borrower = borrowerRepository
				.findByUsername(username)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
						"Borrower not found"));

		return new BorrowerResponse(borrower.getUserId(),borrower.getUserName());
	}
}