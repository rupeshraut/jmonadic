package org.jmonadic.testing;

import java.time.Duration;
import java.time.Instant;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.jmonadic.patterns.Result;
import org.jmonadic.performance.ZeroAllocationException;

/**
 * Chaos engineering utilities for testing exception handling resilience.
 * 
 * Provides controlled failure injection, latency simulation, and resource
 * exhaustion scenarios to validate system behavior under adverse conditions.
 */
public class ChaosEngineering {
    
    private static final Logger logger = LoggerFactory.getLogger(ChaosEngineering.class);
    private static final Random random = new Random();
    
    private final ChaosConfig config;
    private volatile boolean enabled;
    
    public ChaosEngineering(ChaosConfig config) {
        this.config = config;
        this.enabled = config.isEnabled();
    }
    
    /**
     * Wraps an operation with chaos engineering behaviors.
     */
    public <T> Result<T, Exception> chaosWrap(String operationName, Supplier<T> operation) {
        if (!enabled || !shouldInjectChaos()) {
            return executeNormally(operation);
        }
        
        return injectChaos(operationName, operation);
    }
    
    /**
     * Executes operation with potential failure injection.
     */
    private <T> Result<T, Exception> injectChaos(String operationName, Supplier<T> operation) {
        ChaosType chaosType = selectChaosType();
        
        logger.info("🔥 Injecting chaos: {} for operation: {}", chaosType, operationName);
        
        switch (chaosType) {
            case EXCEPTION:
                return injectException(operationName);
            
            case LATENCY:
                return injectLatency(operation);
            
            case TIMEOUT:
                return injectTimeout(operation);
            
            case MEMORY_PRESSURE:
                return injectMemoryPressure(operation);
            
            case CPU_SPIKE:
                return injectCpuSpike(operation);
            
            case INTERMITTENT_FAILURE:
                return injectIntermittentFailure(operation);
            
            default:
                return executeNormally(operation);
        }
    }
    
    /**
     * Injects random exceptions to test error handling.
     */
    private <T> Result<T, Exception> injectException(String operationName) {
        Exception[] exceptions = {
            new RuntimeException("Chaos: Simulated runtime exception"),
            new IllegalStateException("Chaos: Simulated illegal state"),
            new IllegalArgumentException("Chaos: Simulated invalid argument"),
            ZeroAllocationException.of("CHAOS001", "Chaos: Simulated service failure"),
            new java.util.concurrent.TimeoutException("Chaos: Simulated timeout"),
            new java.io.IOException("Chaos: Simulated I/O failure")
        };
        
        Exception exception = exceptions[random.nextInt(exceptions.length)];
        logger.warn("💥 Chaos injected exception: {} in operation: {}", 
                   exception.getClass().getSimpleName(), operationName);
        
        return Result.failure(exception);
    }
    
    /**
     * Injects artificial latency to test timeout handling.
     */
    private <T> Result<T, Exception> injectLatency(Supplier<T> operation) {
        long latencyMs = config.getMinLatency() + 
                        random.nextInt(config.getMaxLatency() - config.getMinLatency());
        
        logger.info("⏰ Chaos injecting {}ms latency", latencyMs);
        
        try {
            Thread.sleep(latencyMs);
            return executeNormally(operation);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.failure(e);
        }
    }
    
    /**
     * Simulates timeout scenarios.
     */
    private <T> Result<T, Exception> injectTimeout(Supplier<T> operation) {
        logger.info("⏱️ Chaos simulating timeout scenario");
        
        // Simulate a long-running operation that would timeout
        try {
            Thread.sleep(config.getTimeoutDuration().toMillis());
            return Result.failure(new java.util.concurrent.TimeoutException("Chaos: Operation timed out"));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.failure(e);
        }
    }
    
    /**
     * Creates memory pressure to test resource handling.
     */
    private <T> Result<T, Exception> injectMemoryPressure(Supplier<T> operation) {
        logger.info("🧠 Chaos injecting memory pressure");
        
        // Allocate temporary memory to create pressure
        byte[][] memoryHog = new byte[100][];
        try {
            for (int i = 0; i < 100; i++) {
                memoryHog[i] = new byte[1024 * 1024]; // 1MB each
            }
            
            return executeNormally(operation);
            
        } catch (OutOfMemoryError e) {
            return Result.failure(new RuntimeException("Chaos: Out of memory", e));
        } finally {
            // Clear memory
            for (int i = 0; i < memoryHog.length; i++) {
                memoryHog[i] = null;
            }
            System.gc();
        }
    }
    
    /**
     * Creates CPU spikes to test performance under load.
     */
    private <T> Result<T, Exception> injectCpuSpike(Supplier<T> operation) {
        logger.info("🔥 Chaos injecting CPU spike");
        
        long startTime = System.currentTimeMillis();
        long spikeEndTime = startTime + config.getCpuSpikeDuration().toMillis();
        
        // Start CPU-intensive work in background thread
        Thread cpuSpike = new Thread(() -> {
            while (System.currentTimeMillis() < spikeEndTime) {
                // Busy waiting to consume CPU
                Math.random();
            }
        });
        cpuSpike.setDaemon(true);
        cpuSpike.start();
        
        try {
            return executeNormally(operation);
        } finally {
            cpuSpike.interrupt();
        }
    }
    
    /**
     * Simulates intermittent failures that come and go.
     */
    private <T> Result<T, Exception> injectIntermittentFailure(Supplier<T> operation) {
        logger.info("🔄 Chaos injecting intermittent failure");
        
        // Fail for a few attempts, then succeed
        if (random.nextBoolean()) {
            return Result.failure(new RuntimeException("Chaos: Intermittent service failure"));
        } else {
            return executeNormally(operation);
        }
    }
    
    /**
     * Executes operation normally without chaos.
     */
    private <T> Result<T, Exception> executeNormally(Supplier<T> operation) {
        return Result.of(operation);
    }
    
    /**
     * Determines if chaos should be injected based on probability.
     */
    private boolean shouldInjectChaos() {
        return random.nextDouble() < config.getFailureProbability();
    }
    
    /**
     * Selects which type of chaos to inject.
     */
    private ChaosType selectChaosType() {
        ChaosType[] types = ChaosType.values();
        return types[random.nextInt(types.length)];
    }
    
    /**
     * Enables or disables chaos engineering.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        logger.info("Chaos engineering {}", enabled ? "ENABLED" : "DISABLED");
    }
    
    /**
     * Types of chaos that can be injected.
     */
    public enum ChaosType {
        EXCEPTION,
        LATENCY,
        TIMEOUT,
        MEMORY_PRESSURE,
        CPU_SPIKE,
        INTERMITTENT_FAILURE
    }
    
    /**
     * Configuration for chaos engineering behavior.
     */
    public static class ChaosConfig {
        private boolean enabled = false;
        private double failureProbability = 0.1; // 10% failure rate
        private int minLatency = 100; // ms
        private int maxLatency = 5000; // ms
        private Duration timeoutDuration = Duration.ofSeconds(10);
        private Duration cpuSpikeDuration = Duration.ofSeconds(1);
        
        public static ChaosConfig disabled() {
            return new ChaosConfig();
        }
        
        public static ChaosConfig aggressive() {
            ChaosConfig config = new ChaosConfig();
            config.enabled = true;
            config.failureProbability = 0.3; // 30% failure rate
            config.minLatency = 500;
            config.maxLatency = 10000;
            return config;
        }
        
        public static ChaosConfig moderate() {
            ChaosConfig config = new ChaosConfig();
            config.enabled = true;
            config.failureProbability = 0.1; // 10% failure rate
            config.minLatency = 100;
            config.maxLatency = 2000;
            return config;
        }
        
        public static ChaosConfig conservative() {
            ChaosConfig config = new ChaosConfig();
            config.enabled = true;
            config.failureProbability = 0.05; // 5% failure rate
            config.minLatency = 50;
            config.maxLatency = 500;
            return config;
        }
        
        // Getters and setters
        public boolean isEnabled() { return enabled; }
        public ChaosConfig enabled(boolean enabled) { this.enabled = enabled; return this; }
        
        public double getFailureProbability() { return failureProbability; }
        public ChaosConfig failureProbability(double probability) { this.failureProbability = probability; return this; }
        
        public int getMinLatency() { return minLatency; }
        public ChaosConfig minLatency(int latency) { this.minLatency = latency; return this; }
        
        public int getMaxLatency() { return maxLatency; }
        public ChaosConfig maxLatency(int latency) { this.maxLatency = latency; return this; }
        
        public Duration getTimeoutDuration() { return timeoutDuration; }
        public ChaosConfig timeoutDuration(Duration duration) { this.timeoutDuration = duration; return this; }
        
        public Duration getCpuSpikeDuration() { return cpuSpikeDuration; }
        public ChaosConfig cpuSpikeDuration(Duration duration) { this.cpuSpikeDuration = duration; return this; }
    }
    
    /**
     * Builder for creating chaos engineering instances.
     */
    public static class Builder {
        private ChaosConfig config = new ChaosConfig();
        
        public Builder enabled(boolean enabled) {
            config.enabled = enabled;
            return this;
        }
        
        public Builder failureProbability(double probability) {
            config.failureProbability = probability;
            return this;
        }
        
        public Builder latencyRange(int min, int max) {
            config.minLatency = min;
            config.maxLatency = max;
            return this;
        }
        
        public Builder timeoutDuration(Duration duration) {
            config.timeoutDuration = duration;
            return this;
        }
        
        public ChaosEngineering build() {
            return new ChaosEngineering(config);
        }
    }
    
    public static Builder builder() {
        return new Builder();
    }
}