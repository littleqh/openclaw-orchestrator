package com.openclaw.orchestrator.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(1)
public class AuthFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String path = httpRequest.getRequestURI();

        // Allow auth endpoints without authentication
        if (path.startsWith("/api/auth/")) {
            chain.doFilter(request, response);
            return;
        }

        // For /api/** paths, authentication is handled by JwtAuthFilter and AgentTokenFilter
        // This filter mainly ensures auth endpoints are accessible
        if (!path.startsWith("/api/")) {
            // Non-API paths (e.g., static resources) pass through
            chain.doFilter(request, response);
            return;
        }

        // Let the more specific filters handle the actual authentication
        chain.doFilter(request, response);
    }
}