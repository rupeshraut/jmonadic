package org.jmonadic.web;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.jmonadic.patterns.Result;
import org.jmonadic.observability.StructuredLogger;
import org.jmonadic.observability.ExceptionMetrics;
import org.jmonadic.web.model.User;
import org.jmonadic.web.model.ApiResponse;
import org.jmonadic.web.service.UserService;
import org.jmonadic.web.exception.UserNotFoundException;
import org.jmonadic.web.exception.ValidationException;

import java.time.Duration;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.annotation.Counted;

/**
 * REST controller demonstrating production-ready exception handling patterns.
 * 
 * Features:
 * - Result pattern for clean error handling
 * - Structured logging with correlation IDs
 * - Metrics collection with Micrometer
 * - Proper HTTP status code mapping
 * - Input validation with meaningful error responses
 */
@RestController
@RequestMapping("/api/v1/users")
@CrossOrigin(origins = "*", maxAge = 3600)
public class UserController {
    
    private static final StructuredLogger logger = StructuredLogger.builder()
        .component("UserController")
        .withApplication("exception-showcase")
        .build();
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private ExceptionMetrics exceptionMetrics;
    
    /**
     * Creates a new user with comprehensive error handling.
     */
    @PostMapping
    @Timed(value = "user.create", description = "Time taken to create a user")
    @Counted(value = "user.create.requests", description = "Number of user creation requests")
    public ResponseEntity<ApiResponse<User>> createUser(@RequestBody User user) {
        String requestId = UUID.randomUUID().toString();
        StructuredLogger.LogContext context = StructuredLogger.LogContext.create()
            .withRequestId(requestId)
            .withOperation("create_user");
        
        // Log the operation start
        logger.logSuccess("createUser", "Starting user creation", context);
        
        return userService.createUser(user)
            .fold(
                createdUser -> {
                    exceptionMetrics.recordSuccess("user.create", Duration.ofMillis(1), "endpoint", "POST /users");
                    logger.logSuccess("createUser", createdUser, context);
                    return ResponseEntity.status(HttpStatus.CREATED)
                        .body(ApiResponse.success(createdUser, "User created successfully"));
                },
                error -> {
                    exceptionMetrics.recordFailure("user.create", error.getClass().getSimpleName(), 
                                                  Duration.ofMillis(1), "endpoint", "POST /users");
                    logger.logFailure("createUser", error, context);
                    
                    if (error instanceof ValidationException) {
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(ApiResponse.error("VALIDATION_ERROR", error.getMessage()));
                    }
                    
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ApiResponse.error("INTERNAL_ERROR", "Failed to create user"));
                }
            );
    }
    
    /**
     * Retrieves a user by ID with proper error handling.
     */
    @GetMapping("/{id}")
    @Timed(value = "user.get", description = "Time taken to retrieve a user")
    public ResponseEntity<ApiResponse<User>> getUser(@PathVariable Long id) {
        String requestId = UUID.randomUUID().toString();
        StructuredLogger.LogContext context = StructuredLogger.LogContext.create()
            .withRequestId(requestId)
            .withOperation("get_user")
            .with("user_id", id);
        
        return userService.findById(id)
            .fold(
                user -> {
                    logger.logSuccess("getUser", user, context);
                    exceptionMetrics.recordSuccess("user.get", Duration.ofMillis(1), "endpoint", "GET /users/{id}");
                    return ResponseEntity.ok(ApiResponse.success(user, "User retrieved successfully"));
                },
                error -> {
                    logger.logFailure("getUser", error, context);
                    exceptionMetrics.recordFailure("user.get", error.getClass().getSimpleName(), 
                                                  Duration.ofMillis(1), "endpoint", "GET /users/{id}");
                    
                    if (error instanceof UserNotFoundException) {
                        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(ApiResponse.error("USER_NOT_FOUND", "User with ID " + id + " not found"));
                    }
                    
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ApiResponse.error("INTERNAL_ERROR", "Failed to retrieve user"));
                }
            );
    }
    
    /**
     * Updates a user with comprehensive validation.
     */
    @PutMapping("/{id}")
    @Timed(value = "user.update", description = "Time taken to update a user")
    public ResponseEntity<ApiResponse<User>> updateUser(@PathVariable Long id, @RequestBody User user) {
        String requestId = UUID.randomUUID().toString();
        StructuredLogger.LogContext context = StructuredLogger.LogContext.create()
            .withRequestId(requestId)
            .withOperation("update_user")
            .with("user_id", id);
        
        // Chain operations with proper error handling
        return userService.findById(id)
            .flatMap(existingUser -> userService.updateUser(id, user))
            .fold(
                updatedUser -> {
                    logger.logSuccess("updateUser", updatedUser, context);
                    return ResponseEntity.ok(ApiResponse.success(updatedUser, "User updated successfully"));
                },
                error -> {
                    logger.logFailure("updateUser", error, context);
                    
                    if (error instanceof UserNotFoundException) {
                        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(ApiResponse.error("USER_NOT_FOUND", "User with ID " + id + " not found"));
                    }
                    
                    if (error instanceof ValidationException) {
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(ApiResponse.error("VALIDATION_ERROR", error.getMessage()));
                    }
                    
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ApiResponse.error("INTERNAL_ERROR", "Failed to update user"));
                }
            );
    }
    
    /**
     * Deletes a user with proper error handling.
     */
    @DeleteMapping("/{id}")
    @Timed(value = "user.delete", description = "Time taken to delete a user")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable Long id) {
        String requestId = UUID.randomUUID().toString();
        StructuredLogger.LogContext context = StructuredLogger.LogContext.create()
            .withRequestId(requestId)
            .withOperation("delete_user")
            .with("user_id", id);
        
        return userService.deleteUser(id)
            .fold(
                result -> {
                    logger.logSuccess("deleteUser", "User deleted", context);
                    return ResponseEntity.ok(ApiResponse.success(null, "User deleted successfully"));
                },
                error -> {
                    logger.logFailure("deleteUser", error, context);
                    
                    if (error instanceof UserNotFoundException) {
                        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(ApiResponse.error("USER_NOT_FOUND", "User with ID " + id + " not found"));
                    }
                    
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ApiResponse.error("INTERNAL_ERROR", "Failed to delete user"));
                }
            );
    }
    
    /**
     * Lists all users with pagination and error handling.
     */
    @GetMapping
    @Timed(value = "user.list", description = "Time taken to list users")
    public ResponseEntity<ApiResponse<List<User>>> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        String requestId = UUID.randomUUID().toString();
        StructuredLogger.LogContext context = StructuredLogger.LogContext.create()
            .withRequestId(requestId)
            .withOperation("list_users")
            .with("page", page)
            .with("size", size);
        
        return userService.findAll(page, size)
            .fold(
                users -> {
                    logger.logSuccess("listUsers", String.format("Retrieved %d users", users.size()), context);
                    return ResponseEntity.ok(ApiResponse.success(users, "Users retrieved successfully"));
                },
                error -> {
                    logger.logFailure("listUsers", error, context);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ApiResponse.error("INTERNAL_ERROR", "Failed to retrieve users"));
                }
            );
    }
    
    /**
     * Searches users by name with fuzzy matching and error handling.
     */
    @GetMapping("/search")
    @Timed(value = "user.search", description = "Time taken to search users")
    public ResponseEntity<ApiResponse<List<User>>> searchUsers(@RequestParam String name) {
        String requestId = UUID.randomUUID().toString();
        StructuredLogger.LogContext context = StructuredLogger.LogContext.create()
            .withRequestId(requestId)
            .withOperation("search_users")
            .with("search_term", name);
        
        // Input validation
        if (name == null || name.trim().length() < 2) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("VALIDATION_ERROR", "Search term must be at least 2 characters"));
        }
        
        return userService.searchByName(name.trim())
            .fold(
                users -> {
                    logger.logSuccess("searchUsers", String.format("Found %d users matching '%s'", users.size(), name), context);
                    return ResponseEntity.ok(ApiResponse.success(users, "Search completed successfully"));
                },
                error -> {
                    logger.logFailure("searchUsers", error, context);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ApiResponse.error("INTERNAL_ERROR", "Search operation failed"));
                }
            );
    }
    
    /**
     * Bulk operations with comprehensive error handling.
     */
    @PostMapping("/bulk")
    @Timed(value = "user.bulk_create", description = "Time taken to create multiple users")
    public ResponseEntity<ApiResponse<List<User>>> createUsers(@RequestBody List<User> users) {
        String requestId = UUID.randomUUID().toString();
        StructuredLogger.LogContext context = StructuredLogger.LogContext.create()
            .withRequestId(requestId)
            .withOperation("bulk_create_users")
            .with("batch_size", users.size());
        
        // Input validation
        if (users == null || users.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("VALIDATION_ERROR", "User list cannot be empty"));
        }
        
        if (users.size() > 100) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("VALIDATION_ERROR", "Batch size cannot exceed 100 users"));
        }
        
        return userService.createUsers(users)
            .fold(
                createdUsers -> {
                    logger.logSuccess("createUsers", String.format("Created %d users", createdUsers.size()), context);
                    return ResponseEntity.status(HttpStatus.CREATED)
                        .body(ApiResponse.success(createdUsers, "Users created successfully"));
                },
                error -> {
                    logger.logFailure("createUsers", error, context);
                    
                    if (error instanceof ValidationException) {
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(ApiResponse.error("VALIDATION_ERROR", error.getMessage()));
                    }
                    
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ApiResponse.error("INTERNAL_ERROR", "Failed to create users"));
                }
            );
    }
}