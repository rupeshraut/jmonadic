package org.jmonadic;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot application demonstrating production-ready exception handling patterns.
 * 
 * This application showcases:
 * - Core functional patterns (Result, Either, Try) integrated with Spring
 * - Web API examples with proper error handling
 * - Observability with metrics and structured logging
 * - Resilience patterns in a Spring context
 */
@SpringBootApplication
public class ExceptionShowcaseApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExceptionShowcaseApplication.class, args);
    }
}