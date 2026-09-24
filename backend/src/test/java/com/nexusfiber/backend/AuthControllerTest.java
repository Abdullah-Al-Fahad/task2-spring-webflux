package com.nexusfiber.backend;

import com.nexusfiber.backend.controller.AuthController;
import com.nexusfiber.backend.controller.dto.AuthRequest;
import com.nexusfiber.backend.controller.dto.AuthResponse;
import com.nexusfiber.backend.domain.User;
import com.nexusfiber.backend.exception.AuthenticationException;
import com.nexusfiber.backend.repository.UserRepository;
import com.nexusfiber.backend.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private AuthController authController;

    @Test
    void testLogin_Success() {
        AuthRequest request = new AuthRequest();
        request.setUsername("operator1");
        request.setPassword("password123");

        User mockUser = new User();
        mockUser.setUsername("operator1");
        mockUser.setPasswordHash("hashed_password");
        mockUser.setRole("OPERATOR");

        when(userRepository.findByUsername("operator1")).thenReturn(Mono.just(mockUser));
        when(passwordEncoder.matches("password123", "hashed_password")).thenReturn(true);
        when(jwtUtil.generateToken("operator1", "OPERATOR")).thenReturn("mock_jwt_token");

        Mono<AuthResponse> responseMono = authController.login(Mono.just(request));

        StepVerifier.create(responseMono)
                .assertNext(response -> {
                    assertEquals("mock_jwt_token", response.getAccess());
                    assertEquals("operator1", response.getUsername());
                    assertEquals("OPERATOR", response.getRole());
                })
                .verifyComplete();
    }

    @Test
    void testLogin_InvalidCredentials() {
        AuthRequest request = new AuthRequest();
        request.setUsername("operator1");
        request.setPassword("wrong_password");

        User mockUser = new User();
        mockUser.setUsername("operator1");
        mockUser.setPasswordHash("hashed_password");

        when(userRepository.findByUsername("operator1")).thenReturn(Mono.just(mockUser));
        when(passwordEncoder.matches("wrong_password", "hashed_password")).thenReturn(false);

        Mono<AuthResponse> responseMono = authController.login(Mono.just(request));

        StepVerifier.create(responseMono)
                .expectError(AuthenticationException.class)
                .verify();
    }

    @Test
    void testRegister_Success() {
        com.nexusfiber.backend.controller.dto.RegisterRequest request = new com.nexusfiber.backend.controller.dto.RegisterRequest();
        request.setUsername("newuser");
        request.setPassword("password123");
        request.setRole(com.nexusfiber.backend.domain.UserRole.OPERATOR);

        User savedUser = new User();
        savedUser.setUsername("newuser");
        savedUser.setRole("OPERATOR");

        when(userRepository.findByUsername("newuser")).thenReturn(Mono.empty());
        when(passwordEncoder.encode("password123")).thenReturn("hashed_password");
        when(userRepository.save(org.mockito.ArgumentMatchers.any(User.class))).thenReturn(Mono.just(savedUser));
        when(jwtUtil.generateToken("newuser", "OPERATOR")).thenReturn("mock_jwt_token");

        Mono<AuthResponse> responseMono = authController.register(Mono.just(request));

        StepVerifier.create(responseMono)
                .assertNext(response -> {
                    assertEquals("mock_jwt_token", response.getAccess());
                    assertEquals("newuser", response.getUsername());
                    assertEquals("OPERATOR", response.getRole());
                })
                .verifyComplete();
    }

    @Test
    void testRegister_DuplicateUsername() {
        com.nexusfiber.backend.controller.dto.RegisterRequest request = new com.nexusfiber.backend.controller.dto.RegisterRequest();
        request.setUsername("existinguser");

        User existingUser = new User();
        existingUser.setUsername("existinguser");

        when(userRepository.findByUsername("existinguser")).thenReturn(Mono.just(existingUser));

        Mono<AuthResponse> responseMono = authController.register(Mono.just(request));

        StepVerifier.create(responseMono)
                .expectError(com.nexusfiber.backend.exception.DuplicateResourceException.class)
                .verify();
    }
}
