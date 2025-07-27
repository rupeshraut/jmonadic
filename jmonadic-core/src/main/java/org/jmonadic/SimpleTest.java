package org.jmonadic;

import org.jmonadic.patterns.Result;

/**
 * Simple standalone test to verify compilation works.
 */
public class SimpleTest {
    
    public static void main(String[] args) {
        System.out.println("Testing Result pattern...");
        
        // Test basic Result functionality
        Result<String, Exception> success = Result.success("Hello World");
        Result<String, Exception> failure = Result.failure(new RuntimeException("Test error"));
        
        System.out.println("Success: " + success.isSuccess());
        System.out.println("Failure: " + failure.isFailure());
        
        // Test mapping
        Result<Integer, Exception> mapped = success.map(String::length);
        System.out.println("Mapped length: " + mapped.getValue());
        
        System.out.println("Basic functionality works!");
    }
}
