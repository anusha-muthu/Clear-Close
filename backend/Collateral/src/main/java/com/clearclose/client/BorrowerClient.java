package com.clearclose.client;

import com.clearclose.dto.CollateralBorrowerResponse;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.client.RestClientException;

@Component
public class BorrowerClient {

	private final RestClient borrowerRestClient;

	public BorrowerClient( @Qualifier("borrowerRestClient") RestClient restClient) {
	    this.borrowerRestClient = restClient;
	}

	public CollateralBorrowerResponse getByUsername(String username) {

	try {
		return borrowerRestClient.get()
				.uri("/internal/borrowers/username/{username}",username)
				.retrieve()
				.body(CollateralBorrowerResponse.class);
		} 
	catch (RestClientException ex) {
			throw new RuntimeException(
					"Unable to retrieve borrower from Borrower Service",ex);
			}
	}
}