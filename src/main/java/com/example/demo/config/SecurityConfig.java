package com.example.demo.config;

import java.security.interfaces.RSAPublicKey;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtPublicKeyProvider jwtPublicKeyProvider;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF protection - not needed for stateless REST APIs
            .csrf(csrf -> csrf.disable())
            
            // Make the application stateless
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            
            // Configure authorization rules
            .authorizeHttpRequests(auth -> auth
                // Public endpoints - no authentication required
                .requestMatchers("/swagger-ui/**", "/api-docs/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                
                // All API endpoints require authentication
                .requestMatchers("/api/v1/**").authenticated()
                
                // Any other request requires authentication
                .anyRequest().authenticated()
            )
            
            // Configure OAuth2 Resource Server with JWT
            .oauth2ResourceServer(oauth2 -> 
                oauth2.jwt(jwt -> jwt
                    .decoder(jwtDecoder())
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
            );

        return http.build();
    }

    /**
     * Configures the JWT decoder using the RSA public key from the auth service
     * Uses lazy initialization to avoid failing at startup if auth service is unavailable
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        // Return a lazy-loading JWT decoder that fetches the public key on first use
        return token -> {
            try {
                RSAPublicKey publicKey = jwtPublicKeyProvider.fetchPublicKey();
                JwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
                log.info("JWT Decoder configured with RSA public key");
                return decoder.decode(token);
            } catch (Exception e) {
                log.error("Failed to decode JWT token", e);
                throw new RuntimeException("Failed to decode JWT token: " + e.getMessage(), e);
            }
        };
    }

    /**
     * Converts JWT claims to Spring Security authorities
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        
        // Configure how to extract authorities from JWT
        // By default, looks for 'scope' or 'scp' claim
        grantedAuthoritiesConverter.setAuthorityPrefix("ROLE_");
        grantedAuthoritiesConverter.setAuthoritiesClaimName("roles");

        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);
        
        return jwtAuthenticationConverter;
    }
}
