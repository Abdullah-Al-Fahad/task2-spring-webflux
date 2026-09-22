package com.nexusfiber.backend.security;

import io.jsonwebtoken.Claims;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Collections;

@Component
public class JwtAuthenticationManager implements ReactiveAuthenticationManager {

    private final JwtUtil jwtUtil;

    public JwtAuthenticationManager(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        String authToken = authentication.getCredentials().toString();
        
        try {
            if (!jwtUtil.validateToken(authToken)) {
                return Mono.empty();
            }
            
            Claims claims = jwtUtil.extractAllClaims(authToken);
            String username = claims.getSubject();
            String role = claims.get("role", String.class);
            
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                username, 
                null, 
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role))
            );
            
            return Mono.just(auth);
        } catch (Exception e) {
            return Mono.empty();
        }
    }
}
