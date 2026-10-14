package com.ofss;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ClearCloseApplication {

	public static void main(String[] args) {
		SpringApplication.run(ClearCloseApplication.class, args);
		System.out.println("Started");
	}

}
