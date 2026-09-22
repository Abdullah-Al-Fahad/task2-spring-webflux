package com.nexusfiber.backend.config;

import com.nexusfiber.backend.security.JwtAuthenticationManager;
import com.nexusfiber.backend.security.SecurityContextRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Mono;
import java.util.Arrays;

/**
 * Security Configuration Layer
 * 
 * Architecture Note: This class completely disables Spring's default session-based (cookie) security
 * in favor of stateless JWT authentication, which is necessary for a scalable WebFlux application.
 */
@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity // Enables method-level security (like @PreAuthorize) if we need it later
public class SecurityConfig {

    // These two components handle parsing the JWT and extracting the User's identity
    private final JwtAuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;

    public SecurityConfig(JwtAuthenticationManager authenticationManager, SecurityContextRepository securityContextRepository) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
    }

    /**
     * Password Encoder Bean
     * 
     * Security Detail: We use BCrypt hashing for all passwords. Spring Security uses this
     * globally whenever a password needs to be checked or encoded.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Security Filter Chain
     * 
     * Implementation Note: This defines the core security rules for every incoming HTTP request.
     * It configures what routes are public, what routes require authentication, and how to handle errors.
     */
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                // 1. Disable CSRF because we use JWTs, which are immune to CSRF if stored correctly (not in cookies)
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                
                // 2. Attach our custom CORS configuration (defined below)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                
                // 3. Disable default login pages and HTTP Basic Auth (since this is a pure API)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                
                // 4. Register our custom JWT parsers to handle authentication
                .authenticationManager(authenticationManager)
                .securityContextRepository(securityContextRepository)
                
                // 5. Define access rules for URL paths
                .authorizeExchange(exchanges -> exchanges
                        // Allow all pre-flight OPTIONS requests (necessary for CORS in browsers)
                        .pathMatchers(HttpMethod.OPTIONS).permitAll()
                        
                        // Publicly accessible endpoints (Registration, Login, Health Checks, and WebSockets)
                        .pathMatchers("/api/token/**").permitAll()
                        .pathMatchers("/api/register/**").permitAll()
                        .pathMatchers("/api/health/**").permitAll()
                        .pathMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/webjars/**").permitAll()
                        .pathMatchers("/ws/**").permitAll()
                        
                        // All other endpoints require a valid JWT token
                        .anyExchange().authenticated()
                )
                
                // 6. Define custom JSON responses for authentication failures
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        // If user has NO token, return 401 Unauthorized
                        .authenticationEntryPoint((swe, e) -> 
                            Mono.fromRunnable(() -> swe.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED))
                        )
                        // If user HAS a token but lacks permission (e.g. Operator trying to do Supervisor action), return 403 Forbidden
                        .accessDeniedHandler((swe, e) -> 
                            Mono.fromRunnable(() -> swe.getResponse().setStatusCode(HttpStatus.FORBIDDEN))
                        )
                )
                .build();
    }

    /**
     * Cross-Origin Resource Sharing (CORS) Configuration
     * 
     * Architecture Note: Since the React frontend and Spring backend run on different ports/domains,
     * the browser will block requests unless the backend explicitly allows them via CORS headers.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Allow requests from any origin (in production, this should be restricted to the exact frontend domain)
        configuration.addAllowedOriginPattern("*");
        
        // Allow standard REST HTTP methods
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        
        // Allow all headers (including our "Authorization" header for the JWT)
        configuration.setAllowedHeaders(Arrays.asList("*"));
        
        // Allow credentials (like cookies or authorization headers) to be sent across origins
        configuration.setAllowCredentials(true);
        
        // Apply this configuration to all endpoints in the application ("/**")
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
