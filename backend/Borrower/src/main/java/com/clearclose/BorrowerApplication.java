package com.clearclose;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BorrowerApplication {

	public static void main(String[] args) {
		SpringApplication.run(BorrowerApplication.class, args);
		System.out.println("started");
	}

}
