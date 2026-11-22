package com.capstone.web;

import com.capstone.db.AuthManager;
import com.capstone.db.PasswordHasher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.util.Map;

/** Register, log in (returns a bearer token) and log out. */
@RestController
@RequestMapping("/api/auth")
class AuthController {

    record Credentials(String username, String password) {}

    private final AuthManager auth = new AuthManager();
    private final SessionStore sessions;

    AuthController(SessionStore sessions) { this.sessions = sessions; }

    @PostMapping("/register")
    ResponseEntity<Map<String, String>> register(@RequestBody Credentials c) {
        String username = c.username() == null ? "" : c.username().trim();
        if (username.isEmpty() || username.length() > 100) throw ApiException.badRequest("Username must be 1–100 characters");
        if (c.password() == null || c.password().length() < 6) throw ApiException.badRequest("Password must be at least 6 characters");
        if (!PasswordHasher.isAcceptableLength(c.password())) throw ApiException.badRequest("Password is too long (max 72 bytes)");
        return switch (auth.register(username, c.password())) {
            case OK -> ResponseEntity.status(HttpStatus.CREATED).body(Map.of("username", username));
            case USERNAME_TAKEN -> throw new ApiException(HttpStatus.CONFLICT, "Username already taken");
            case INVALID -> throw ApiException.badRequest("Invalid username or password");
            case ERROR -> throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Database unavailable, try again shortly");
        };
    }

    @PostMapping("/login")
    Map<String, String> login(@RequestBody Credentials c) throws SQLException {
        String username = c.username() == null ? "" : c.username().trim();
        int userId = auth.login(username, c.password());
        if (userId < 0) throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        return Map.of("token", sessions.create(userId, username), "username", username);
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest request) {
        sessions.remove(AuthInterceptor.bearerToken(request));
        return ResponseEntity.noContent().build();
    }
}
