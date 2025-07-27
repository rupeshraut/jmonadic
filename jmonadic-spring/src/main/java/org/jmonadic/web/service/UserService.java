package org.jmonadic.web.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.jmonadic.patterns.Result;
import org.jmonadic.web.model.User;
import org.jmonadic.web.repository.UserRepository;
import org.jmonadic.web.exception.UserNotFoundException;
import org.jmonadic.web.exception.ValidationException;
import org.jmonadic.observability.StructuredLogger;
import org.jmonadic.resilience.CircuitBreaker;
import org.jmonadic.resilience.RetryPolicy;

/**
 * User service demonstrating production-ready exception handling patterns
 * with database operations, validation, and resilience mechanisms.
 */
@Service
@Transactional
public class UserService {
    
    private static final StructuredLogger logger = StructuredLogger.builder()
        .component("UserService")
        .withApplication("exception-showcase")
        .build();
    
    @Autowired
    private UserRepository userRepository;
    
    private final CircuitBreaker databaseCircuitBreaker;
    private final RetryPolicy retryPolicy;
    
    public UserService() {
        this.databaseCircuitBreaker = CircuitBreaker.builder()
            .name("UserDatabase")
            .failureThreshold(5)
            .waitDurationInOpenState(java.time.Duration.ofSeconds(30))
            .build();
        
        this.retryPolicy = RetryPolicy.builder()
            .name("UserServiceRetry")
            .maxAttempts(3)
            .initialDelay(java.time.Duration.ofMillis(100))
            .build();
    }
    
    /**
     * Creates a new user with comprehensive validation.
     */
    public Result<User, Exception> createUser(User user) {
        return validateUser(user)
            .flatMap(validUser -> executeWithResilience(() -> {
                // Check for duplicate email
                if (userRepository.findByEmail(validUser.getEmail()).isPresent()) {
                    throw new RuntimeException("Email already exists: " + validUser.getEmail());
                }
                
                User savedUser = userRepository.save(validUser);
                logger.logSuccess("createUser", savedUser, 
                    StructuredLogger.LogContext.create().with("user_id", savedUser.getId()));
                
                return savedUser;
            }));
    }
    
    /**
     * Creates multiple users in a single transaction.
     */
    public Result<List<User>, Exception> createUsers(List<User> users) {
        return Result.of(() -> {
            // Validate all users first
            for (User user : users) {
                Result<User, Exception> validation = validateUser(user);
                if (validation.isFailure()) {
                    throw new RuntimeException(validation.getError().getMessage(), validation.getError());
                }
            }
            
            // Check for duplicate emails
            for (User user : users) {
                if (userRepository.findByEmail(user.getEmail()).isPresent()) {
                    throw new RuntimeException("Email already exists: " + user.getEmail());
                }
            }
            
            // Save all users
            List<User> savedUsers = userRepository.saveAll(users);
            logger.logSuccess("createUsers", String.format("Created %d users", savedUsers.size()),
                StructuredLogger.LogContext.create().with("batch_size", savedUsers.size()));
            
            return savedUsers;
        });
    }
    
    /**
     * Finds a user by ID with proper error handling.
     */
    @Transactional(readOnly = true)
    public Result<User, Exception> findById(Long id) {
        if (id == null || id <= 0) {
            return Result.failure(new RuntimeException("User ID must be positive"));
        }
        
        return executeWithResilience(() -> {
            return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + id));
        });
    }
    
    /**
     * Updates an existing user.
     */
    public Result<User, Exception> updateUser(Long id, User userUpdate) {
        return findById(id)
            .flatMap(existingUser -> validateUser(userUpdate))
            .flatMap(validUpdate -> executeWithResilience(() -> {
                User existingUser = userRepository.findById(id).orElseThrow();
                
                // Update fields
                existingUser.setName(validUpdate.getName());
                existingUser.setAge(validUpdate.getAge());
                existingUser.setPhoneNumber(validUpdate.getPhoneNumber());
                
                // Check if email is being changed and if new email exists
                if (!existingUser.getEmail().equals(validUpdate.getEmail())) {
                    if (userRepository.findByEmail(validUpdate.getEmail()).isPresent()) {
                        throw new RuntimeException("Email already exists: " + validUpdate.getEmail());
                    }
                    existingUser.setEmail(validUpdate.getEmail());
                }
                
                User savedUser = userRepository.save(existingUser);
                logger.logSuccess("updateUser", savedUser,
                    StructuredLogger.LogContext.create().with("user_id", savedUser.getId()));
                
                return savedUser;
            }));
    }
    
    /**
     * Deletes a user by ID.
     */
    public Result<Void, Exception> deleteUser(Long id) {
        return findById(id)
            .flatMap(user -> executeWithResilience(() -> {
                userRepository.deleteById(id);
                logger.logSuccess("deleteUser", "User deleted",
                    StructuredLogger.LogContext.create().with("user_id", id));
                return null;
            }));
    }
    
    /**
     * Finds all users with pagination.
     */
    @Transactional(readOnly = true)
    public Result<List<User>, Exception> findAll(int page, int size) {
        if (page < 0) {
            return Result.failure(new ValidationException("Page number must be non-negative"));
        }
        if (size <= 0 || size > 100) {
            return Result.failure(new ValidationException("Page size must be between 1 and 100"));
        }
        
        return executeWithResilience(() -> {
            Pageable pageable = PageRequest.of(page, size);
            List<User> users = userRepository.findAll(pageable).getContent();
            
            logger.logSuccess("findAll", String.format("Retrieved %d users", users.size()),
                StructuredLogger.LogContext.create()
                    .with("page", page)
                    .with("size", size)
                    .with("result_count", users.size()));
            
            return users;
        });
    }
    
    /**
     * Searches users by name with fuzzy matching.
     */
    @Transactional(readOnly = true)
    public Result<List<User>, Exception> searchByName(String name) {
        if (name == null || name.trim().length() < 2) {
            return Result.failure(new RuntimeException("Search term must be at least 2 characters"));
        }
        
        return executeWithResilience(() -> {
            String searchTerm = "%" + name.trim().toLowerCase() + "%";
            List<User> users = userRepository.findByNameContainingIgnoreCase(searchTerm);
            
            logger.logSuccess("searchByName", String.format("Found %d users matching '%s'", users.size(), name),
                StructuredLogger.LogContext.create()
                    .with("search_term", name)
                    .with("result_count", users.size()));
            
            return users;
        });
    }
    
    /**
     * Validates user data with comprehensive checks.
     */
    private Result<User, Exception> validateUser(User user) {
        if (user == null) {
            return Result.failure(new ValidationException("User cannot be null"));
        }
        
        if (user.getName() == null || user.getName().trim().isEmpty()) {
            return Result.failure(new ValidationException("Name is required"));
        }
        
        if (user.getName().length() < 2 || user.getName().length() > 100) {
            return Result.failure(new ValidationException("Name must be between 2 and 100 characters"));
        }
        
        if (user.getEmail() == null || user.getEmail().trim().isEmpty()) {
            return Result.failure(new ValidationException("Email is required"));
        }
        
        if (!isValidEmail(user.getEmail())) {
            return Result.failure(new ValidationException("Email format is invalid"));
        }
        
        if (user.getAge() == null || user.getAge() < 18 || user.getAge() > 120) {
            return Result.failure(new ValidationException("Age must be between 18 and 120"));
        }
        
        if (user.getPhoneNumber() != null && !isValidPhoneNumber(user.getPhoneNumber())) {
            return Result.failure(new ValidationException("Phone number format is invalid"));
        }
        
        return Result.success(user);
    }
    
    /**
     * Executes database operations with circuit breaker and retry protection.
     */
    private <T> Result<T, Exception> executeWithResilience(java.util.function.Supplier<T> operation) {
        return retryPolicy.executeWithCircuitBreaker(operation, databaseCircuitBreaker);
    }
    
    /**
     * Validates email format using regex.
     */
    private boolean isValidEmail(String email) {
        String emailRegex = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$";
        return email.matches(emailRegex);
    }
    
    /**
     * Validates phone number format.
     */
    private boolean isValidPhoneNumber(String phoneNumber) {
        String phoneRegex = "^\\+?[1-9]\\d{1,14}$";
        return phoneNumber.matches(phoneRegex);
    }
}