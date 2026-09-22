package com.nexusfiber.backend.controller;

import com.nexusfiber.backend.controller.dto.AuthRequest;
import com.nexusfiber.backend.controller.dto.AuthResponse;
import com.nexusfiber.backend.controller.dto.RegisterRequest;
import com.nexusfiber.backend.domain.User;
import com.nexusfiber.backend.exception.AuthenticationException;
import com.nexusfiber.backend.exception.DuplicateResourceException;
import com.nexusfiber.backend.repository.UserRepository;
import com.nexusfiber.backend.security.JwtUtil;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import java.time.ZonedDateTime;

/**
 * Authentication and Registration Controller
 * 
 * Architecture Note: This controller handles identity verification and issues stateless JWT tokens.
 * By using JWTs instead of server-side sessions, the backend remains completely stateless,
 * allowing it to scale horizontally behind a load balancer without needing "sticky sessions".
 */
@Slf4j
@RestController
@RequestMapping("/api")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    /**
     * Login Endpoint: Authenticates a user and returns a JWT pair.
     * 
     * Implementation Detail: The method returns a Mono (reactive stream) meaning it won't block
     * the web server thread while waiting for the database query or password hashing algorithm.
     */
    @PostMapping("/token/")
    public Mono<AuthResponse> login(@Valid @RequestBody Mono<AuthRequest> requestMono) {
        return requestMono.flatMap(request ->
                userRepository.findByUsername(request.getUsername())
                        //  Verify the hashed password against the plaintext input
                        .filter(user -> passwordEncoder.matches(request.getPassword(), user.getPasswordHash()))
                        //  If valid, generate the JWT token and DTO response
                        .map(this::buildAuthResponse)
                        //  If invalid (or user not found), throw an exception which is caught by GlobalExceptionHandler
                        .switchIfEmpty(Mono.error(new AuthenticationException("Invalid credentials")))
                        .doOnSuccess(resp -> log.info("User '{}' authenticated successfully", request.getUsername()))
        );
    }

    /**
     * Registration Endpoint: Creates a new user in the system.
     * 
     * Implementation Detail: @ResponseStatus(HttpStatus.CREATED) automatically sets the 
     * HTTP response code to 201 instead of the default 200 upon success.
     */
    @PostMapping("/register/")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<AuthResponse> register(@Valid @RequestBody Mono<RegisterRequest> requestMono) {
        return requestMono.flatMap(request ->
                // First, check if a user with this username already exists
                userRepository.findByUsername(request.getUsername())
                        .flatMap(existing -> Mono.<AuthResponse>error(
                                new DuplicateResourceException("Username '" + request.getUsername() + "' already exists")))
                        
                        // If no user was found (which is good), proceed with creation
                        .switchIfEmpty(
                                // Mono.defer ensures this execution block doesn't run unless switchIfEmpty is triggered
                                Mono.defer(() -> {
                                    User newUser = User.builder()
                                            .username(request.getUsername())
                                            // Security: NEVER store plaintext passwords. Hash them with BCrypt first.
                                            .passwordHash(passwordEncoder.encode(request.getPassword()))
                                            .role(request.getRole().name())
                                            .isActive(true)
                                            .dateJoined(ZonedDateTime.now())
                                            .build();

                                    // Save the new user to the database and instantly log them in by issuing a JWT
                                    return userRepository.save(newUser)
                                            .map(this::buildAuthResponse)
                                            .doOnSuccess(resp -> log.info("User '{}' registered with role '{}'",
                                                    newUser.getUsername(), newUser.getRole()));
                                })
                        )
        );
    }

    /**
     * Helper Method: Constructs the JSON response containing the generated JWT token.
     */
    private AuthResponse buildAuthResponse(User user) {
        String token = jwtUtil.generateToken(user.getUsername(), user.getRole());
        return AuthResponse.builder()
                .access(token)
                .refresh(token) // Note: In a production app, the refresh token should have a longer expiration
                .role(user.getRole())
                .username(user.getUsername())
                .build();
    }
}
