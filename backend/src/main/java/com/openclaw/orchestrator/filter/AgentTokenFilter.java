package com.openclaw.orchestrator.filter;

import com.openclaw.orchestrator.entity.AgentToken;
import com.openclaw.orchestrator.service.AgentTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
@RequiredArgsConstructor
public class AgentTokenFilter extends OncePerRequestFilter {

    private final AgentTokenService agentTokenService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Agent ")) {
            String token = authHeader.substring(6);
            AgentToken agentToken = agentTokenService.validateToken(token);

            String clientIp = getClientIp(request);
            String path = request.getRequestURI();

            if (agentToken != null) {
                agentTokenService.logAccess(agentToken, path, clientIp, true);

                // Set authentication for Agent
                String principal = "AGENT:" + (agentToken.getWorker() != null ? agentToken.getWorker().getId() : "SYSTEM");
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                principal,
                                null,
                                Collections.singletonList(new SimpleGrantedAuthority("ROLE_AGENT"))
                        );
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } else {
                // Log failed access
                if (token != null) {
                    AgentToken dummyToken = new AgentToken();
                    dummyToken.setId(-1L);
                    agentTokenService.logAccess(dummyToken, path, clientIp, false);
                }
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"message\":\"Invalid token\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}