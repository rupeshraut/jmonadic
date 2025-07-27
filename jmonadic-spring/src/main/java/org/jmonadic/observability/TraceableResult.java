package org.jmonadic.observability;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

import org.jmonadic.patterns.Result;

/**
 * A Result implementation with built-in distributed tracing support.
 * 
 * Automatically creates spans for operations, tracks success/failure outcomes,
 * and propagates trace context across service boundaries.
 */
public class TraceableResult {
    
    private static final AttributeKey<String> OPERATION_TYPE = AttributeKey.stringKey("operation.type");
    private static final AttributeKey<String> ERROR_TYPE = AttributeKey.stringKey("error.type");
    private static final AttributeKey<String> ERROR_MESSAGE = AttributeKey.stringKey("error.message");
    private static final AttributeKey<Long> OPERATION_COUNT = AttributeKey.longKey("operation.count");
    
    private final Tracer tracer;
    private final Map<String, Long> operationCounters = new ConcurrentHashMap<>();
    
    public TraceableResult(OpenTelemetry openTelemetry, String instrumentationName) {
        this.tracer = openTelemetry.getTracer(instrumentationName, "1.0.0");
    }
    
    /**
     * Executes a supplier within a traced span with automatic error handling.
     */
    public <T> Result<T, Exception> trace(String operationName, Supplier<T> supplier) {
        return trace(operationName, SpanKind.INTERNAL, supplier);
    }
    
    /**
     * Executes a supplier within a traced span with specified span kind.
     */
    public <T> Result<T, Exception> trace(String operationName, SpanKind spanKind, Supplier<T> supplier) {
        SpanBuilder spanBuilder = tracer.spanBuilder(operationName)
            .setSpanKind(spanKind)
            .setAttribute(OPERATION_TYPE, "exception.pattern")
            .setAttribute(OPERATION_COUNT, incrementOperationCount(operationName));
        
        Span span = spanBuilder.startSpan();
        
        try (Scope scope = span.makeCurrent()) {
            T result = supplier.get();
            
            span.setStatus(StatusCode.OK);
            span.setAttribute("result.success", true);
            
            return Result.success(result);
            
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.setAttribute("result.success", false);
            span.setAttribute(ERROR_TYPE, e.getClass().getSimpleName());
            span.setAttribute(ERROR_MESSAGE, e.getMessage());
            
            // Record exception as span event
            span.recordException(e, Attributes.of(
                AttributeKey.stringKey("exception.escaped"), "false"
            ));
            
            return Result.failure(e);
            
        } finally {
            span.end();
        }
    }
    
    /**
     * Traces a Result-returning operation with enhanced context.
     */
    public <T, E> Result<T, E> traceResult(String operationName, Supplier<Result<T, E>> supplier) {
        return traceResult(operationName, SpanKind.INTERNAL, supplier);
    }
    
    /**
     * Traces a Result-returning operation with specified span kind and context.
     */
    public <T, E> Result<T, E> traceResult(String operationName, SpanKind spanKind, 
                                          Supplier<Result<T, E>> supplier) {
        SpanBuilder spanBuilder = tracer.spanBuilder(operationName)
            .setSpanKind(spanKind)
            .setAttribute(OPERATION_TYPE, "result.pattern")
            .setAttribute(OPERATION_COUNT, incrementOperationCount(operationName));
        
        Span span = spanBuilder.startSpan();
        
        try (Scope scope = span.makeCurrent()) {
            Result<T, E> result = supplier.get();
            
            if (result.isSuccess()) {
                span.setStatus(StatusCode.OK);
                span.setAttribute("result.success", true);
            } else {
                span.setStatus(StatusCode.ERROR, "Operation failed");
                span.setAttribute("result.success", false);
                
                E error = result.getError();
                if (error != null) {
                    span.setAttribute(ERROR_TYPE, error.getClass().getSimpleName());
                    span.setAttribute(ERROR_MESSAGE, error.toString());
                    
                    // Record as span event for structured analysis
                    span.addEvent("result.failure", Attributes.of(
                        AttributeKey.stringKey("error.details"), error.toString()
                    ));
                }
            }
            
            return result;
            
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, "Unexpected exception");
            span.recordException(e);
            
            // Convert to Result failure
            @SuppressWarnings("unchecked")
            Result<T, E> failureResult = (Result<T, E>) Result.failure(e);
            return failureResult;
            
        } finally {
            span.end();
        }
    }
    
    /**
     * Creates a child span for nested operations.
     */
    public <T> Result<T, Exception> traceChild(String operationName, Supplier<T> supplier) {
        Context parentContext = Context.current();
        
        SpanBuilder spanBuilder = tracer.spanBuilder(operationName)
            .setParent(parentContext)
            .setSpanKind(SpanKind.INTERNAL)
            .setAttribute(OPERATION_TYPE, "nested.operation");
        
        Span span = spanBuilder.startSpan();
        
        try (Scope scope = span.makeCurrent()) {
            T result = supplier.get();
            span.setStatus(StatusCode.OK);
            return Result.success(result);
            
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            return Result.failure(e);
            
        } finally {
            span.end();
        }
    }
    
    /**
     * Creates a traced wrapper for external service calls.
     */
    public <T> TracedServiceCall<T> serviceCall(String serviceName, String operation) {
        return new TracedServiceCall<>(tracer, serviceName, operation);
    }
    
    /**
     * Adds custom attributes to the current span.
     */
    public TraceableResult withAttribute(String key, String value) {
        Span currentSpan = Span.current();
        if (currentSpan != null) {
            currentSpan.setAttribute(key, value);
        }
        return this;
    }
    
    /**
     * Adds custom attributes to the current span.
     */
    public TraceableResult withAttribute(String key, long value) {
        Span currentSpan = Span.current();
        if (currentSpan != null) {
            currentSpan.setAttribute(key, value);
        }
        return this;
    }
    
    /**
     * Adds a custom event to the current span.
     */
    public TraceableResult addEvent(String name, Map<String, String> attributes) {
        Span currentSpan = Span.current();
        if (currentSpan != null) {
            // Simple event without attributes for now
            currentSpan.addEvent(name);
        }
        return this;
    }
    
    private long incrementOperationCount(String operationName) {
        return operationCounters.merge(operationName, 1L, Long::sum);
    }
    
    /**
     * Wrapper for external service calls with enhanced tracing.
     */
    public static class TracedServiceCall<T> {
        private final Tracer tracer;
        private final String serviceName;
        private final String operation;
        private final Map<String, String> attributes = new ConcurrentHashMap<>();
        
        private TracedServiceCall(Tracer tracer, String serviceName, String operation) {
            this.tracer = tracer;
            this.serviceName = serviceName;
            this.operation = operation;
        }
        
        public TracedServiceCall<T> withAttribute(String key, String value) {
            attributes.put(key, value);
            return this;
        }
        
        public TracedServiceCall<T> withUserId(String userId) {
            return withAttribute("user.id", userId);
        }
        
        public TracedServiceCall<T> withRequestId(String requestId) {
            return withAttribute("request.id", requestId);
        }
        
        public Result<T, Exception> execute(Supplier<T> supplier) {
            SpanBuilder spanBuilder = tracer.spanBuilder(serviceName + "." + operation)
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute("service.name", serviceName)
                .setAttribute("service.operation", operation);
            
            // Add custom attributes
            attributes.forEach((key, value) -> 
                spanBuilder.setAttribute(AttributeKey.stringKey(key), value));
            
            Span span = spanBuilder.startSpan();
            
            try (Scope scope = span.makeCurrent()) {
                long startTime = System.currentTimeMillis();
                T result = supplier.get();
                long duration = System.currentTimeMillis() - startTime;
                
                span.setStatus(StatusCode.OK);
                span.setAttribute("service.call.duration_ms", duration);
                span.setAttribute("service.call.success", true);
                
                return Result.success(result);
                
            } catch (Exception e) {
                span.setStatus(StatusCode.ERROR, "Service call failed");
                span.setAttribute("service.call.success", false);
                span.recordException(e, Attributes.of(
                    AttributeKey.stringKey("service.error.type"), e.getClass().getSimpleName()
                ));
                
                return Result.failure(e);
                
            } finally {
                span.end();
            }
        }
        
        public Result<T, Exception> executeWithTimeout(Supplier<T> supplier, long timeoutMs) {
            return withAttribute("timeout.ms", String.valueOf(timeoutMs))
                .execute(supplier);
        }
    }
    
    /**
     * Builder for creating TraceableResult instances.
     */
    public static class Builder {
        private OpenTelemetry openTelemetry;
        private String instrumentationName = "exception-showcase";
        
        public Builder openTelemetry(OpenTelemetry openTelemetry) {
            this.openTelemetry = openTelemetry;
            return this;
        }
        
        public Builder instrumentationName(String instrumentationName) {
            this.instrumentationName = instrumentationName;
            return this;
        }
        
        public TraceableResult build() {
            if (openTelemetry == null) {
                throw new IllegalStateException("OpenTelemetry instance is required");
            }
            return new TraceableResult(openTelemetry, instrumentationName);
        }
    }
    
    public static Builder builder() {
        return new Builder();
    }
}