package org.jmonadic.observability;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.jmonadic.patterns.Result;

/**
 * Structured logging implementation for exception handling patterns.
 * 
 * Provides JSON-formatted logs with rich context, correlation IDs,
 * and performance metrics for comprehensive observability.
 */
public class StructuredLogger {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final Logger logger;
    private final String component;
    private final Map<String, Object> defaultContext;
    
    public StructuredLogger(Class<?> clazz) {
        this(clazz.getSimpleName());
    }
    
    public StructuredLogger(String component) {
        this.logger = LoggerFactory.getLogger(component);
        this.component = component;
        this.defaultContext = new ConcurrentHashMap<>();
        
        // Set default context
        defaultContext.put("component", component);
        defaultContext.put("version", "1.0.0");
    }
    
    /**
     * Logs a successful operation with structured context.
     */
    public void logSuccess(String operation, Object result, LogContext context) {
        ObjectNode logEntry = createBaseLogEntry("SUCCESS", operation, context);
        
        if (result != null) {
            try {
                logEntry.set("result", objectMapper.valueToTree(result));
            } catch (Exception e) {
                logEntry.put("result", result.toString());
            }
        }
        
        logger.info(logEntry.toString());
    }
    
    /**
     * Logs a failed operation with error details and context.
     */
    public void logFailure(String operation, Throwable error, LogContext context) {
        ObjectNode logEntry = createBaseLogEntry("FAILURE", operation, context);
        
        // Add error details
        ObjectNode errorDetails = objectMapper.createObjectNode();
        errorDetails.put("type", error.getClass().getSimpleName());
        errorDetails.put("message", error.getMessage());
        
        if (error.getCause() != null) {
            errorDetails.put("cause", error.getCause().getMessage());
        }
        
        // Add stack trace for debugging (limited depth)
        if (context.includeStackTrace()) {
            StackTraceElement[] stackTrace = error.getStackTrace();
            int maxDepth = Math.min(stackTrace.length, 10);
            StringBuilder stackBuilder = new StringBuilder();
            
            for (int i = 0; i < maxDepth; i++) {
                stackBuilder.append(stackTrace[i].toString()).append("\n");
            }
            errorDetails.put("stackTrace", stackBuilder.toString());
        }
        
        logEntry.set("error", errorDetails);
        
        logger.error(logEntry.toString());
    }
    
    /**
     * Logs a Result with automatic success/failure determination.
     */
    public <T, E> void logResult(String operation, Result<T, E> result, LogContext context) {
        if (result.isSuccess()) {
            logSuccess(operation, result.getValue(), context);
        } else {
            E error = result.getError();
            if (error instanceof Throwable) {
                logFailure(operation, (Throwable) error, context);
            } else {
                logFailure(operation, new RuntimeException(error.toString()), context);
            }
        }
    }
    
    /**
     * Logs performance metrics for an operation.
     */
    public void logPerformance(String operation, long durationMs, LogContext context) {
        ObjectNode logEntry = createBaseLogEntry("PERFORMANCE", operation, context);
        
        ObjectNode performance = objectMapper.createObjectNode();
        performance.put("duration_ms", durationMs);
        performance.put("duration_category", categorizeDuration(durationMs));
        
        logEntry.set("performance", performance);
        
        logger.info(logEntry.toString());
    }
    
    /**
     * Logs circuit breaker state changes.
     */
    public void logCircuitBreakerEvent(String circuitBreakerName, String event, String state, LogContext context) {
        ObjectNode logEntry = createBaseLogEntry("CIRCUIT_BREAKER", "state_change", context);
        
        ObjectNode circuitBreakerData = objectMapper.createObjectNode();
        circuitBreakerData.put("name", circuitBreakerName);
        circuitBreakerData.put("event", event);
        circuitBreakerData.put("state", state);
        
        logEntry.set("circuit_breaker", circuitBreakerData);
        
        logger.warn(logEntry.toString());
    }
    
    /**
     * Logs security-related events.
     */
    public void logSecurityEvent(String event, String userId, String resource, boolean success, LogContext context) {
        ObjectNode logEntry = createBaseLogEntry("SECURITY", event, context);
        
        ObjectNode securityData = objectMapper.createObjectNode();
        securityData.put("user_id", userId);
        securityData.put("resource", resource);
        securityData.put("success", success);
        securityData.put("event_type", event);
        
        logEntry.set("security", securityData);
        
        if (success) {
            logger.info(logEntry.toString());
        } else {
            logger.warn(logEntry.toString());
        }
    }
    
    /**
     * Creates a wrapper that automatically logs operations.
     */
    public <T> LoggingWrapper<T> wrap(String operation) {
        return new LoggingWrapper<>(this, operation);
    }
    
    /**
     * Creates the base log entry structure.
     */
    private ObjectNode createBaseLogEntry(String level, String operation, LogContext context) {
        ObjectNode logEntry = objectMapper.createObjectNode();
        
        // Timestamp
        logEntry.put("timestamp", Instant.now().toString());
        logEntry.put("level", level);
        
        // Operation details
        logEntry.put("operation", operation);
        logEntry.put("component", component);
        
        // Add MDC context
        Map<String, String> mdcContext = MDC.getCopyOfContextMap();
        if (mdcContext != null && !mdcContext.isEmpty()) {
            ObjectNode mdcNode = objectMapper.createObjectNode();
            mdcContext.forEach(mdcNode::put);
            logEntry.set("mdc", mdcNode);
        }
        
        // Add default context
        ObjectNode contextNode = objectMapper.createObjectNode();
        defaultContext.forEach((key, value) -> {
            if (value instanceof String) {
                contextNode.put(key, (String) value);
            } else if (value instanceof Number) {
                contextNode.put(key, value.toString());
            } else {
                contextNode.put(key, value.toString());
            }
        });
        
        // Add custom context
        if (context != null) {
            context.getAttributes().forEach((key, value) -> {
                if (value instanceof String) {
                    contextNode.put(key, (String) value);
                } else if (value instanceof Number) {
                    contextNode.put(key, value.toString());
                } else {
                    contextNode.put(key, value.toString());
                }
            });
        }
        
        logEntry.set("context", contextNode);
        
        return logEntry;
    }
    
    private String categorizeDuration(long durationMs) {
        if (durationMs < 10) return "fast";
        if (durationMs < 100) return "normal";
        if (durationMs < 1000) return "slow";
        return "very_slow";
    }
    
    /**
     * Context object for structured logging.
     */
    public static class LogContext {
        private final Map<String, Object> attributes = new ConcurrentHashMap<>();
        private boolean includeStackTrace = false;
        
        public static LogContext create() {
            return new LogContext();
        }
        
        public LogContext with(String key, Object value) {
            attributes.put(key, value);
            return this;
        }
        
        public LogContext withUserId(String userId) {
            return with("user_id", userId);
        }
        
        public LogContext withRequestId(String requestId) {
            return with("request_id", requestId);
        }
        
        public LogContext withSessionId(String sessionId) {
            return with("session_id", sessionId);
        }
        
        public LogContext withCorrelationId(String correlationId) {
            return with("correlation_id", correlationId);
        }
        
        public LogContext withOperation(String operation) {
            return with("operation_type", operation);
        }
        
        public LogContext withMetric(String name, Object value) {
            return with("metric." + name, value);
        }
        
        public LogContext includeStackTrace(boolean include) {
            this.includeStackTrace = include;
            return this;
        }
        
        public boolean includeStackTrace() {
            return includeStackTrace;
        }
        
        public Map<String, Object> getAttributes() {
            return attributes;
        }
    }
    
    /**
     * Wrapper for automatic logging around operations.
     */
    public static class LoggingWrapper<T> {
        private final StructuredLogger logger;
        private final String operation;
        private final LogContext context;
        
        private LoggingWrapper(StructuredLogger logger, String operation) {
            this.logger = logger;
            this.operation = operation;
            this.context = LogContext.create();
        }
        
        public LoggingWrapper<T> withContext(LogContext context) {
            this.context.getAttributes().putAll(context.getAttributes());
            return this;
        }
        
        public LoggingWrapper<T> withUserId(String userId) {
            context.withUserId(userId);
            return this;
        }
        
        public LoggingWrapper<T> withRequestId(String requestId) {
            context.withRequestId(requestId);
            return this;
        }
        
        public Result<T, Exception> execute(java.util.function.Supplier<T> supplier) {
            long startTime = System.currentTimeMillis();
            
            try {
                T result = supplier.get();
                long duration = System.currentTimeMillis() - startTime;
                
                logger.logSuccess(operation, result, context.withMetric("duration_ms", duration));
                return Result.success(result);
                
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - startTime;
                
                logger.logFailure(operation, e, context.withMetric("duration_ms", duration));
                return Result.failure(e);
            }
        }
        
        public Result<T, Exception> executeResult(java.util.function.Supplier<Result<T, Exception>> supplier) {
            long startTime = System.currentTimeMillis();
            
            try {
                Result<T, Exception> result = supplier.get();
                long duration = System.currentTimeMillis() - startTime;
                
                logger.logResult(operation, result, context.withMetric("duration_ms", duration));
                return result;
                
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - startTime;
                
                logger.logFailure(operation, e, context.withMetric("duration_ms", duration));
                return Result.failure(e);
            }
        }
    }
    
    /**
     * Builder for creating StructuredLogger instances.
     */
    public static class Builder {
        private String component;
        private final Map<String, Object> defaultContext = new ConcurrentHashMap<>();
        
        public Builder component(String component) {
            this.component = component;
            return this;
        }
        
        public Builder withDefault(String key, Object value) {
            defaultContext.put(key, value);
            return this;
        }
        
        public Builder withApplication(String applicationName) {
            return withDefault("application", applicationName);
        }
        
        public Builder withVersion(String version) {
            return withDefault("version", version);
        }
        
        public Builder withEnvironment(String environment) {
            return withDefault("environment", environment);
        }
        
        public StructuredLogger build() {
            if (component == null) {
                throw new IllegalStateException("Component name is required");
            }
            
            StructuredLogger logger = new StructuredLogger(component);
            logger.defaultContext.putAll(defaultContext);
            return logger;
        }
    }
    
    public static Builder builder() {
        return new Builder();
    }
}