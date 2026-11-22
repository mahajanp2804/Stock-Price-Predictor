package com.capstone.web;

import com.capstone.db.DBConnection;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Connection;
import java.util.Map;

/** GET /api/health — 200 when the database answers, 503 otherwise. No login needed. */
@RestController
class HealthController {

    @GetMapping("/api/health")
    ResponseEntity<Map<String, String>> health() {
        try (Connection c = DBConnection.get()) {
            return ResponseEntity.ok(Map.of("status", "up", "database", c.isValid(2) ? "up" : "down"));
        } catch (Exception e) {
            return ResponseEntity.status(503).body(Map.of("status", "degraded", "database", "down"));
        }
    }
}
