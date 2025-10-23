package com.example.demo.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Authentication verification and user info endpoints")
public class AuthTestController {

    @Operation(
        summary = "Get current user information",
        description = "Returns information about the currently authenticated user from the JWT token",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "User information retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token")
    })
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser(@AuthenticationPrincipal Jwt jwt) {
        // Extract user information from JWT
        String subject = jwt.getSubject();
        Map<String, Object> claims = jwt.getClaims();
        
        // Return user info
        return ResponseEntity.ok(Map.of(
            "subject", subject,
            "email", claims.getOrDefault("email", "N/A"),
            "name", claims.getOrDefault("name", "N/A"),
            "roles", claims.getOrDefault("roles", "N/A"),
            "tokenIssuedAt", jwt.getIssuedAt(),
            "tokenExpiresAt", jwt.getExpiresAt()
        ));
    }

    @Operation(
        summary = "Verify authentication",
        description = "Simple endpoint to verify that JWT authentication is working correctly",
        security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Authentication verified successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token")
    })
    @GetMapping("/verify")
    public ResponseEntity<Map<String, Object>> verifyAuthentication(Authentication authentication) {
        return ResponseEntity.ok(Map.of(
            "authenticated", authentication.isAuthenticated(),
            "principal", authentication.getName(),
            "authorities", authentication.getAuthorities(),
            "message", "JWT authentication is working correctly!"
        ));
    }
}
