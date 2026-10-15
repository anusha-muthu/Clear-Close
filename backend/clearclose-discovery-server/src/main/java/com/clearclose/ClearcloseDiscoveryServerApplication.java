package com.clearclose;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

@SpringBootApplication
@EnableEurekaServer 
public class ClearcloseDiscoveryServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(ClearcloseDiscoveryServerApplication.class, args);
		System.out.println("Eureka Server");
	}

}
