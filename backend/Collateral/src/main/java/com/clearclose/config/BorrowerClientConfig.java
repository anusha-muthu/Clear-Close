package com.clearclose.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class BorrowerClientConfig{

	@Bean
	public RestClient borrowerRestClient(
			@Value("${services.borrower.base-url}") String baseUrl) {

		return RestClient.builder()
				.baseUrl(baseUrl)
				.build();
	}
}