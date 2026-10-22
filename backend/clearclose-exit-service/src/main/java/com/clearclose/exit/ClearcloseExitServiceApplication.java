
package com.clearclose.exit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

import com.clearclose.exit.config.SecurityConfig;

@SpringBootApplication
@Import(SecurityConfig.class)
public class ClearcloseExitServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClearcloseExitServiceApplication.class, args);
    }
}
