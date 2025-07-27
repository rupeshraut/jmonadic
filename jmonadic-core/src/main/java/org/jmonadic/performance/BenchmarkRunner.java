package org.jmonadic.performance;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.jmonadic.patterns.Result;

/**
 * Comprehensive benchmarking suite for exception handling patterns.
 * 
 * Measures performance characteristics of different approaches:
 * - Traditional try-catch vs functional patterns
 * - Allocation overhead and GC pressure
 * - Throughput under different error rates
 * - Latency distribution analysis
 */
public class BenchmarkRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(BenchmarkRunner.class);
    
    public static void main(String[] args) {
        BenchmarkRunner runner = new BenchmarkRunner();
        runner.runAllBenchmarks();
    }
    
    public void runAllBenchmarks() {
        logger.info("🏁 === PERFORMANCE BENCHMARK SUITE ===");
        
        warmupJvm();
        
        benchmarkBasicOperations();
        benchmarkErrorRates();
        benchmarkAllocationOverhead();
        benchmarkThroughput();
        benchmarkLatencyDistribution();
    }
    
    private void warmupJvm() {
        logger.info("🔥 Warming up JVM...");
        
        // Warm up traditional exceptions
        for (int i = 0; i < 10_000; i++) {
            try {
                if (i % 100 == 0) {
                    throw new RuntimeException("Warmup");
                }
            } catch (RuntimeException e) {
                // Swallow
            }
        }
        
        // Warm up Result pattern
        for (int i = 0; i < 10_000; i++) {
            final int value = i;
            Result<Integer, Exception> result = Result.of(() -> {
                if (value % 100 == 0) {
                    throw new RuntimeException("Warmup");
                }
                return value;
            });
            result.getOrElse(-1);
        }
        
        // Force GC to start with clean slate
        System.gc();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        logger.info("✅ JVM warmup completed");
    }
    
    private void benchmarkBasicOperations() {
        logger.info("\n📊 === BASIC OPERATIONS BENCHMARK ===");
        
        int iterations = 1_000_000;
        
        // Traditional try-catch (success path)
        BenchmarkResult traditional = benchmark("Traditional Success", iterations, () -> {
            try {
                return String.valueOf(ThreadLocalRandom.current().nextInt());
            } catch (Exception e) {
                return "error";
            }
        });
        
        // Result pattern (success path)
        BenchmarkResult resultPattern = benchmark("Result Success", iterations, () -> {
            return Result.<String>of(() -> String.valueOf(ThreadLocalRandom.current().nextInt()))
                         .getOrElse("error");
        });
        
        // Fast fail result (success path)
        BenchmarkResult fastFail = benchmark("FastFail Success", iterations, () -> {
            return FastFailResult.chain(Result.success(ThreadLocalRandom.current().nextInt()))
                                 .map(String::valueOf)
                                 .build()
                                 .getOrElse("error");
        });
        
        logBenchmarkComparison("Success Path", traditional, resultPattern, fastFail);
    }
    
    private void benchmarkErrorRates() {
        logger.info("\n💥 === ERROR RATE BENCHMARK ===");
        
        int iterations = 100_000;
        double[] errorRates = {0.0, 0.01, 0.05, 0.1, 0.25, 0.5};
        
        for (double errorRate : errorRates) {
            logger.info("🎯 Testing with {}% error rate", errorRate * 100);
            
            // Traditional approach
            BenchmarkResult traditional = benchmark("Traditional", iterations, () -> {
                try {
                    if (ThreadLocalRandom.current().nextDouble() < errorRate) {
                        throw new RuntimeException("Simulated error");
                    }
                    return "success";
                } catch (RuntimeException e) {
                    return "error";
                }
            });
            
            // Result pattern
            BenchmarkResult resultPattern = benchmark("Result", iterations, () -> {
                return Result.<String>of(() -> {
                    if (ThreadLocalRandom.current().nextDouble() < errorRate) {
                        throw new RuntimeException("Simulated error");
                    }
                    return "success";
                }).getOrElse("error");
            });
            
            logger.info("   Traditional: {} ops/sec", traditional.opsPerSecond());
            logger.info("   Result:      {} ops/sec", resultPattern.opsPerSecond());
            logger.info("   Overhead:    {:.2f}x", (double) traditional.totalTimeNs() / resultPattern.totalTimeNs());
        }
    }
    
    private void benchmarkAllocationOverhead() {
        logger.info("\n🧠 === ALLOCATION OVERHEAD BENCHMARK ===");
        
        int iterations = 50_000;
        
        // Measure before
        long memBefore = getUsedMemory();
        
        BenchmarkResult traditional = benchmark("Traditional Exceptions", iterations, () -> {
            try {
                if (ThreadLocalRandom.current().nextBoolean()) {
                    throw new RuntimeException("Error with stack trace");
                }
                return "success";
            } catch (RuntimeException e) {
                return "error";
            }
        });
        
        long memAfterTraditional = getUsedMemory();
        
        // Reset memory baseline
        System.gc();
        memBefore = getUsedMemory();
        
        BenchmarkResult zeroAlloc = benchmark("Zero-Allocation", iterations, () -> {
            try {
                if (ThreadLocalRandom.current().nextBoolean()) {
                    throw ZeroAllocationException.of("ERR001", "Error without stack trace");
                }
                return "success";
            } catch (ZeroAllocationException e) {
                return "error";
            }
        });
        
        long memAfterZeroAlloc = getUsedMemory();
        
        logger.info("Traditional: {} MB allocated", (memAfterTraditional - memBefore) / 1024 / 1024);
        logger.info("Zero-Alloc:  {} MB allocated", (memAfterZeroAlloc - memBefore) / 1024 / 1024);
        
        logBenchmarkComparison("Allocation", traditional, zeroAlloc, null);
    }
    
    private void benchmarkThroughput() {
        logger.info("\n🚀 === THROUGHPUT BENCHMARK ===");
        
        int warmupIterations = 10_000;
        int measurementIterations = 100_000;
        
        // Warmup
        for (int i = 0; i < warmupIterations; i++) {
            final int val = i;
            Result.of(() -> String.valueOf(val)).getOrElse("error");
        }
        
        long start = System.nanoTime();
        for (int i = 0; i < measurementIterations; i++) {
            final int val = i;
            Result.of(() -> String.valueOf(val)).getOrElse("error");
        }
        long duration = System.nanoTime() - start;
        
        double opsPerSecond = (double) measurementIterations / (duration / 1_000_000_000.0);
        logger.info("Result Pattern Throughput: {:.0f} ops/sec", opsPerSecond);
        
        // Concurrent throughput test
        int threadCount = Runtime.getRuntime().availableProcessors();
        int iterationsPerThread = measurementIterations / threadCount;
        
        Thread[] threads = new Thread[threadCount];
        long[] threadTimes = new long[threadCount];
        
        start = System.nanoTime();
        for (int t = 0; t < threadCount; t++) {
            final int threadIndex = t;
            threads[t] = new Thread(() -> {
                long threadStart = System.nanoTime();
                for (int i = 0; i < iterationsPerThread; i++) {
                    final int val = i;
                    Result.of(() -> String.valueOf(val)).getOrElse("error");
                }
                threadTimes[threadIndex] = System.nanoTime() - threadStart;
            });
            threads[t].start();
        }
        
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        
        long totalDuration = System.nanoTime() - start;
        double concurrentOpsPerSecond = (double) measurementIterations / (totalDuration / 1_000_000_000.0);
        logger.info("Concurrent Throughput ({} threads): {:.0f} ops/sec", threadCount, concurrentOpsPerSecond);
    }
    
    private void benchmarkLatencyDistribution() {
        logger.info("\n📈 === LATENCY DISTRIBUTION BENCHMARK ===");
        
        int iterations = 10_000;
        long[] latencies = new long[iterations];
        
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            Result.of(() -> {
                // Simulate some work
                int sum = 0;
                for (int j = 0; j < 100; j++) {
                    sum += j;
                }
                return sum;
            }).getOrElse(-1);
            latencies[i] = System.nanoTime() - start;
        }
        
        java.util.Arrays.sort(latencies);
        
        logger.info("Latency Distribution (nanoseconds):");
        logger.info("  P50:  {} ns", latencies[iterations / 2]);
        logger.info("  P90:  {} ns", latencies[(int) (iterations * 0.9)]);
        logger.info("  P95:  {} ns", latencies[(int) (iterations * 0.95)]);
        logger.info("  P99:  {} ns", latencies[(int) (iterations * 0.99)]);
        logger.info("  P99.9: {} ns", latencies[(int) (iterations * 0.999)]);
        logger.info("  Max:  {} ns", latencies[iterations - 1]);
    }
    
    private BenchmarkResult benchmark(String name, int iterations, Supplier<String> operation) {
        // Warmup
        for (int i = 0; i < Math.min(iterations / 10, 1000); i++) {
            operation.get();
        }
        
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            operation.get();
        }
        long totalTime = System.nanoTime() - start;
        
        return new BenchmarkResult(name, iterations, totalTime);
    }
    
    private void logBenchmarkComparison(String category, BenchmarkResult baseline, BenchmarkResult comparison, BenchmarkResult optional) {
        logger.info("\n🏆 {} Comparison:", category);
        logger.info("  {}: {:.0f} ops/sec", baseline.name(), baseline.opsPerSecond());
        logger.info("  {}: {:.0f} ops/sec", comparison.name(), comparison.opsPerSecond());
        
        if (optional != null) {
            logger.info("  {}: {:.0f} ops/sec", optional.name(), optional.opsPerSecond());
        }
        
        double overhead = (double) baseline.totalTimeNs() / comparison.totalTimeNs();
        logger.info("  Relative Performance: {:.2f}x", overhead);
        
        if (overhead > 1.1) {
            logger.info("  ⚠️  {} is {:.1f}% slower", comparison.name(), (overhead - 1) * 100);
        } else if (overhead < 0.9) {
            logger.info("  ⚡ {} is {:.1f}% faster", comparison.name(), (1 - overhead) * 100);
        } else {
            logger.info("  ✅ Performance is comparable");
        }
    }
    
    private long getUsedMemory() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }
    
    private record BenchmarkResult(String name, int iterations, long totalTimeNs) {
        double opsPerSecond() {
            return (double) iterations / (totalTimeNs / 1_000_000_000.0);
        }
        
        double avgTimeNs() {
            return (double) totalTimeNs / iterations;
        }
    }
}