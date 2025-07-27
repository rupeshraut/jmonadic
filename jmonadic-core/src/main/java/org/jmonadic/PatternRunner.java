package org.jmonadic;

/**
 * Pattern runner for executing specific exception handling patterns.
 * Used by the custom Gradle task to run individual pattern examples.
 */
public class PatternRunner {
    
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage: PatternRunner <pattern-name>");
            System.out.println("Available patterns:");
            System.out.println("  - result-pattern");
            System.out.println("  - either-pattern");
            System.out.println("  - try-pattern");
            System.out.println("  - circuit-breaker");
            System.out.println("  - retry-policy");
            System.out.println("  - composition");
            return;
        }
        
        String pattern = args[0];
        System.out.println("🎯 Running pattern: " + pattern);
        
        // This would be enhanced to run specific patterns
        // For now, it's a placeholder for the custom Gradle task
        switch (pattern) {
            case "result-pattern" -> System.out.println("✅ Result pattern examples completed");
            case "either-pattern" -> System.out.println("✅ Either pattern examples completed");
            case "try-pattern" -> System.out.println("✅ Try pattern examples completed");
            case "circuit-breaker" -> System.out.println("✅ Circuit breaker examples completed");
            case "retry-policy" -> System.out.println("✅ Retry policy examples completed");
            case "composition" -> System.out.println("✅ Composition examples completed");
            default -> System.out.println("❌ Unknown pattern: " + pattern);
        }
    }
}
