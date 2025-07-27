package org.jmonadic.web.model;

import java.time.Instant;
import java.util.List;
import java.util.ArrayList;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Standardized API response wrapper that provides consistent structure
 * for all REST API responses, including success and error cases.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    
    private boolean success;
    private T data;
    private String message;
    private String errorCode;
    private List<ValidationError> errors;
    private Instant timestamp;
    private String requestId;
    
    // Private constructor to enforce factory methods
    private ApiResponse() {
        this.timestamp = Instant.now();
    }
    
    /**
     * Creates a successful response with data.
     */
    public static <T> ApiResponse<T> success(T data) {
        return success(data, null);
    }
    
    /**
     * Creates a successful response with data and message.
     */
    public static <T> ApiResponse<T> success(T data, String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.success = true;
        response.data = data;
        response.message = message;
        return response;
    }
    
    /**
     * Creates an error response with error code and message.
     */
    public static <T> ApiResponse<T> error(String errorCode, String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.success = false;
        response.errorCode = errorCode;
        response.message = message;
        return response;
    }
    
    /**
     * Creates an error response with validation errors.
     */
    public static <T> ApiResponse<T> validationError(String message, List<ValidationError> errors) {
        ApiResponse<T> response = new ApiResponse<>();
        response.success = false;
        response.errorCode = "VALIDATION_ERROR";
        response.message = message;
        response.errors = errors;
        return response;
    }
    
    /**
     * Creates an error response from an exception.
     */
    public static <T> ApiResponse<T> fromException(Exception exception) {
        return error("INTERNAL_ERROR", exception.getMessage());
    }
    
    // Builder pattern for complex responses
    public static <T> Builder<T> builder() {
        return new Builder<>();
    }
    
    // Getters and Setters
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public T getData() {
        return data;
    }
    
    public void setData(T data) {
        this.data = data;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }
    
    public List<ValidationError> getErrors() {
        return errors;
    }
    
    public void setErrors(List<ValidationError> errors) {
        this.errors = errors;
    }
    
    public Instant getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getRequestId() {
        return requestId;
    }
    
    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
    
    /**
     * Builder for creating complex API responses.
     */
    public static class Builder<T> {
        private final ApiResponse<T> response;
        
        private Builder() {
            this.response = new ApiResponse<>();
        }
        
        public Builder<T> success(boolean success) {
            response.success = success;
            return this;
        }
        
        public Builder<T> data(T data) {
            response.data = data;
            return this;
        }
        
        public Builder<T> message(String message) {
            response.message = message;
            return this;
        }
        
        public Builder<T> errorCode(String errorCode) {
            response.errorCode = errorCode;
            return this;
        }
        
        public Builder<T> errors(List<ValidationError> errors) {
            response.errors = errors;
            return this;
        }
        
        public Builder<T> addError(String field, String message) {
            if (response.errors == null) {
                response.errors = new ArrayList<>();
            }
            response.errors.add(new ValidationError(field, message));
            return this;
        }
        
        public Builder<T> requestId(String requestId) {
            response.requestId = requestId;
            return this;
        }
        
        public Builder<T> timestamp(Instant timestamp) {
            response.timestamp = timestamp;
            return this;
        }
        
        public ApiResponse<T> build() {
            return response;
        }
    }
    
    /**
     * Represents a validation error for a specific field.
     */
    public static class ValidationError {
        private String field;
        private String message;
        private Object rejectedValue;
        
        public ValidationError() {}
        
        public ValidationError(String field, String message) {
            this.field = field;
            this.message = message;
        }
        
        public ValidationError(String field, String message, Object rejectedValue) {
            this.field = field;
            this.message = message;
            this.rejectedValue = rejectedValue;
        }
        
        // Getters and Setters
        public String getField() {
            return field;
        }
        
        public void setField(String field) {
            this.field = field;
        }
        
        public String getMessage() {
            return message;
        }
        
        public void setMessage(String message) {
            this.message = message;
        }
        
        public Object getRejectedValue() {
            return rejectedValue;
        }
        
        public void setRejectedValue(Object rejectedValue) {
            this.rejectedValue = rejectedValue;
        }
        
        @Override
        public String toString() {
            return "ValidationError{" +
                    "field='" + field + '\'' +
                    ", message='" + message + '\'' +
                    ", rejectedValue=" + rejectedValue +
                    '}';
        }
    }
    
    @Override
    public String toString() {
        return "ApiResponse{" +
                "success=" + success +
                ", data=" + data +
                ", message='" + message + '\'' +
                ", errorCode='" + errorCode + '\'' +
                ", errors=" + errors +
                ", timestamp=" + timestamp +
                ", requestId='" + requestId + '\'' +
                '}';
    }
}