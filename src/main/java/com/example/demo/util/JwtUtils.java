package com.example.demo.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Utility class for extracting user information from JWT tokens
 */
public class JwtUtils {

    /**
     * Get the current authenticated user's ID from JWT
     * 
     * @return User ID (subject claim) or null if not authenticated
     */
    public static String getCurrentUserId() {
        Jwt jwt = getCurrentJwt();
        return jwt != null ? jwt.getSubject() : null;
    }

    /**
     * Get the current authenticated user's email from JWT
     * 
     * @return User email or null if not available
     */
    public static String getCurrentUserEmail() {
        Jwt jwt = getCurrentJwt();
        return jwt != null ? jwt.getClaim("email") : null;
    }

    /**
     * Get the current authenticated user's name from JWT
     * 
     * @return User name or null if not available
     */
    public static String getCurrentUserName() {
        Jwt jwt = getCurrentJwt();
        return jwt != null ? jwt.getClaim("name") : null;
    }

    /**
     * Get a specific claim from the current user's JWT
     * 
     * @param claimName Name of the claim to retrieve
     * @return Claim value or null if not available
     */
    public static <T> T getClaim(String claimName) {
        Jwt jwt = getCurrentJwt();
        return jwt != null ? jwt.getClaim(claimName) : null;
    }

    /**
     * Get the current JWT token
     * 
     * @return JWT token or null if not authenticated
     */
    public static Jwt getCurrentJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt) {
            return (Jwt) authentication.getPrincipal();
        }
        return null;
    }

    /**
     * Check if the current user is authenticated
     * 
     * @return true if authenticated, false otherwise
     */
    public static boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated();
    }

    /**
     * Check if the current user has a specific role
     * 
     * @param role Role name (without ROLE_ prefix)
     * @return true if user has the role, false otherwise
     */
    public static boolean hasRole(String role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_" + role));
    }
}
