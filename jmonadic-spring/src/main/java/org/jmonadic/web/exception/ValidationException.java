package org.jmonadic.web.exception;

import java.util.List;
import java.util.ArrayList;

/**
 * Exception thrown when validation rules are violated.
 * 
 * This is a business logic exception that should result in a 400 HTTP response.
 */
public class ValidationException extends Exception {
    
    private final String field;
    private final Object rejectedValue;
    private final List<ValidationError> errors;
    
    public ValidationException(String message) {
        super(message);
        this.field = null;
        this.rejectedValue = null;
        this.errors = new ArrayList<>();
    }
    
    public ValidationException(String message, String field) {
        super(message);
        this.field = field;
        this.rejectedValue = null;
        this.errors = new ArrayList<>();
    }
    
    public ValidationException(String message, String field, Object rejectedValue) {
        super(message);
        this.field = field;
        this.rejectedValue = rejectedValue;
        this.errors = new ArrayList<>();
    }
    
    public ValidationException(String message, List<ValidationError> errors) {
        super(message);
        this.field = null;
        this.rejectedValue = null;
        this.errors = new ArrayList<>(errors);
    }
    
    public ValidationException(String message, Throwable cause) {
        super(message, cause);
        this.field = null;
        this.rejectedValue = null;
        this.errors = new ArrayList<>();
    }
    
    public String getField() {
        return field;
    }
    
    public Object getRejectedValue() {
        return rejectedValue;
    }
    
    public List<ValidationError> getErrors() {
        return errors;
    }
    
    public void addError(ValidationError error) {
        this.errors.add(error);
    }
    
    public void addError(String field, String message) {
        this.errors.add(new ValidationError(field, message));
    }
    
    public void addError(String field, String message, Object rejectedValue) {
        this.errors.add(new ValidationError(field, message, rejectedValue));
    }
    
    public boolean hasErrors() {
        return !errors.isEmpty();
    }
    
    /**
     * Represents a single validation error.
     */
    public static class ValidationError {
        private final String field;
        private final String message;
        private final Object rejectedValue;
        
        public ValidationError(String field, String message) {
            this.field = field;
            this.message = message;
            this.rejectedValue = null;
        }
        
        public ValidationError(String field, String message, Object rejectedValue) {
            this.field = field;
            this.message = message;
            this.rejectedValue = rejectedValue;
        }
        
        public String getField() {
            return field;
        }
        
        public String getMessage() {
            return message;
        }
        
        public Object getRejectedValue() {
            return rejectedValue;
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
        return "ValidationException{" +
                "message='" + getMessage() + '\'' +
                ", field='" + field + '\'' +
                ", rejectedValue=" + rejectedValue +
                ", errors=" + errors +
                '}';
    }
}