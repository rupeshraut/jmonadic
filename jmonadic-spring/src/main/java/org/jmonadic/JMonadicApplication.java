package org.jmonadic;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot application demonstrating JMonadic functional programming patterns.
 * 
 * This application showcases:
 * - Core monadic types (Result, Either, Try) integrated with Spring
 * - Web API examples with functional error handling
 * - Observability with metrics and structured logging
 * - Resilience patterns in a Spring context
 */
@SpringBootApplication
public class JMonadicApplication {

    public static void main(String[] args) {
        SpringApplication.run(JMonadicApplication.class, args);
    }
}