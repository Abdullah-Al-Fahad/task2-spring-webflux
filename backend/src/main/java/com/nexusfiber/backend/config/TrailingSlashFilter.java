package com.nexusfiber.backend.config;

import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import org.springframework.http.server.reactive.ServerHttpRequest;

@Component
public class TrailingSlashFilter implements WebFilter {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if ((path.startsWith("/webjars/") || path.startsWith("/swagger-ui") || path.startsWith("/v3/")) 
                && path.endsWith("/") && path.length() > 1) {
            String newPath = path.substring(0, path.length() - 1);
            ServerHttpRequest newRequest = exchange.getRequest().mutate().path(newPath).build();
            return chain.filter(exchange.mutate().request(newRequest).build());
        }
        return chain.filter(exchange);
    }
}
