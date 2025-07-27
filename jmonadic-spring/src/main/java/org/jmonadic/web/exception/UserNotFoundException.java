package org.jmonadic.web.exception;

/**
 * Exception thrown when a requested user cannot be found.
 * 
 * This is a business logic exception that should result in a 404 HTTP response.
 */
public class UserNotFoundException extends Exception {
    
    private final Long userId;
    
    public UserNotFoundException(String message) {
        super(message);
        this.userId = null;
    }
    
    public UserNotFoundException(String message, Long userId) {
        super(message);
        this.userId = userId;
    }
    
    public UserNotFoundException(String message, Throwable cause) {
        super(message, cause);
        this.userId = null;
    }
    
    public UserNotFoundException(String message, Long userId, Throwable cause) {
        super(message, cause);
        this.userId = userId;
    }
    
    public Long getUserId() {
        return userId;
    }
    
    @Override
    public String toString() {
        return "UserNotFoundException{" +
                "message='" + getMessage() + '\'' +
                ", userId=" + userId +
                '}';
    }
}