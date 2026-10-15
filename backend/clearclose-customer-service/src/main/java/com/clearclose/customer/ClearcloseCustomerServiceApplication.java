package com.clearclose.customer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ClearcloseCustomerServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(ClearcloseCustomerServiceApplication.class, args);
		System.out.println("Customer Service Started");
	}

}
