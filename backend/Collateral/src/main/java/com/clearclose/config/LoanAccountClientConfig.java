package com.clearclose.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class LoanAccountClientConfig{

	@Bean
	public RestClient loanAccountRestClient(
			@Value("${services.loanAccount.base-url}") String baseUrl) {

		return RestClient.builder()
				.baseUrl(baseUrl)
				.build();
	}
}