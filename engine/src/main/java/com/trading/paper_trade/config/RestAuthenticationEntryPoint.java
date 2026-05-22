package com.trading.paper_trade.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Returns a JSON error body (instead of an empty 401 / container error page) when a
 * request reaches a secured endpoint with missing or invalid credentials.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("WWW-Authenticate", "Basic realm=\"paper-trade\"");
        response.getWriter().write(
                "{\"status\":401,\"error\":\"Unauthorized\","
                        + "\"message\":\"Authentication required: provide valid HTTP Basic credentials.\"}");
    }
}
