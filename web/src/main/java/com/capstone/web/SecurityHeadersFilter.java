package com.capstone.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Browser hardening headers. The front end loads only its own files, so the
 * Content-Security-Policy allows nothing from other origins and no inline script.
 */
@Component
class SecurityHeadersFilter extends OncePerRequestFilter {

    static final String CSP = "default-src 'self'; script-src 'self'; style-src 'self'; "
        + "img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("Content-Security-Policy", CSP);
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Referrer-Policy", "no-referrer");
        if (request.getRequestURI().startsWith("/api/")) response.setHeader("Cache-Control", "no-store");
        chain.doFilter(request, response);
    }
}
