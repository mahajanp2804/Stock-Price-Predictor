package com.capstone.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** Requires "Authorization: Bearer <token>" on /api routes other than auth and health. */
@Component
class AuthInterceptor implements HandlerInterceptor {

    static final String SESSION = "session";

    private final SessionStore sessions;

    AuthInterceptor(SessionStore sessions) { this.sessions = sessions; }

    static String bearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        return header != null && header.startsWith("Bearer ") ? header.substring(7).trim() : null;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        var session = sessions.find(bearerToken(request));
        if (session.isEmpty()) {
            response.setStatus(401);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Please log in\"}");
            return false;
        }
        request.setAttribute(SESSION, session.get());
        return true;
    }
}
