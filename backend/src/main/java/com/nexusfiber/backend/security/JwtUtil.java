package com.nexusfiber.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT Utility Component
 * 
 * Implementation Detail: This class handles the cryptographic generation and validation
 * of JSON Web Tokens. It ensures that tokens cannot be forged or tampered with by the client.
 */
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expiration;

    // We inject the secret and expiration time from application.yml
    public JwtUtil(@Value("${jwt.secret}") String secret,
                   @Value("${jwt.expiration}") long expiration) {
        // The secret is converted into an HMAC-SHA secret key for cryptographic signing
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = expiration;
    }

    /**
     * Generates a new JWT when a user successfully logs in or registers.
     * 
     * Security Detail: We embed the user's role ("Supervisor" or "Operator") directly into the token.
     * This means the backend doesn't need to query the database to check roles on every API call,
     * drastically improving performance.
     */
    public String generateToken(String username, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", "ROLE_" + role);
        
        return Jwts.builder()
                .claims(claims)
                .subject(username) // The standard JWT field for identifying the user
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(key) // Sign it so the client cannot alter the role or username
                .compact();
    }

    /**
     * Extracts all claims (payload data) from a given token.
     * Throws an exception if the signature is invalid (meaning a hacker tampered with it).
     */
    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    public String extractRole(String token) {
        return extractAllClaims(token).get("role", String.class);
    }

    public boolean isTokenExpired(String token) {
        return extractAllClaims(token).getExpiration().before(new Date());
    }

    public boolean validateToken(String token) {
        try {
            return !isTokenExpired(token);
        } catch (Exception e) {
            // If parsing fails for ANY reason (expired, invalid signature, malformed), it's invalid.
            return false;
        }
    }
}
