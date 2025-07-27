package org.jmonadic.web.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.jmonadic.web.model.User;

/**
 * Repository interface for User entities with advanced query methods
 * demonstrating database exception handling scenarios.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    /**
     * Finds a user by email address.
     */
    Optional<User> findByEmail(String email);
    
    /**
     * Finds users by name containing the given string (case-insensitive).
     */
    @Query("SELECT u FROM User u WHERE LOWER(u.name) LIKE LOWER(:name) AND u.active = true")
    List<User> findByNameContainingIgnoreCase(@Param("name") String name);
    
    /**
     * Finds all active users.
     */
    List<User> findByActiveTrue();
    
    /**
     * Finds users by age range.
     */
    List<User> findByAgeBetween(Integer minAge, Integer maxAge);
    
    /**
     * Finds users created after a specific date.
     */
    @Query("SELECT u FROM User u WHERE u.createdAt > :date AND u.active = true")
    List<User> findUsersCreatedAfter(@Param("date") java.time.LocalDateTime date);
    
    /**
     * Counts active users.
     */
    long countByActiveTrue();
    
    /**
     * Finds users with pagination and active filter.
     */
    Page<User> findByActiveTrue(Pageable pageable);
    
    /**
     * Custom query for complex search scenarios.
     */
    @Query("SELECT u FROM User u WHERE " +
           "(LOWER(u.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) AND " +
           "u.active = true")
    List<User> searchUsers(@Param("searchTerm") String searchTerm);
    
    /**
     * Checks if email exists (for validation).
     */
    boolean existsByEmail(String email);
    
    /**
     * Checks if email exists for a different user (for update validation).
     */
    @Query("SELECT COUNT(u) > 0 FROM User u WHERE u.email = :email AND u.id != :excludeId")
    boolean existsByEmailAndIdNot(@Param("email") String email, @Param("excludeId") Long excludeId);
}