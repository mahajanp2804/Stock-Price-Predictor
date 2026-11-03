package com.capstone.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;

/** Registration and login. Both hit the DB and hash passwords — call off the Swing UI thread. */
public class AuthManager {

    private static final Logger log = LoggerFactory.getLogger(AuthManager.class);

    public enum RegisterResult { OK, USERNAME_TAKEN, INVALID, ERROR }

    public RegisterResult register(String username, String plainPassword) {
        if (username == null || username.isBlank() || !PasswordHasher.isAcceptableLength(plainPassword)) return RegisterResult.INVALID;
        String hashed = PasswordHasher.hash(plainPassword);
        String sql    = "INSERT INTO users (username, password) VALUES (?, ?)";
        try (Connection conn = DBConnection.get(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username.trim()); ps.setString(2, hashed); ps.executeUpdate(); return RegisterResult.OK;
        } catch (SQLIntegrityConstraintViolationException e) {
            return RegisterResult.USERNAME_TAKEN;
        } catch (SQLException e) {
            log.error("Registration failed for {}", username, e);
            return RegisterResult.ERROR;
        }
    }

    /** Returns the user id, or -1 if the credentials are wrong. Throws if the DB is unreachable. */
    public int login(String username, String plainPassword) throws SQLException {
        if (username == null || username.isBlank() || plainPassword == null || plainPassword.isBlank()) return -1;
        String sql = "SELECT id, password FROM users WHERE username = ?";
        try (Connection conn = DBConnection.get(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return -1;
                int id = rs.getInt("id");
                String stored = rs.getString("password");
                if (!PasswordHasher.verify(plainPassword, stored)) return -1;
                if (PasswordHasher.needsRehash(stored)) upgradeHash(conn, id, plainPassword);
                return id;
            }
        }
    }

    private void upgradeHash(Connection conn, int userId, String plainPassword) {
        if (!PasswordHasher.isAcceptableLength(plainPassword)) return;
        try (PreparedStatement ps = conn.prepareStatement("UPDATE users SET password = ? WHERE id = ?")) {
            ps.setString(1, PasswordHasher.hash(plainPassword)); ps.setInt(2, userId); ps.executeUpdate();
            log.info("Upgraded password hash for user {}", userId);
        } catch (SQLException e) {
            log.warn("Could not upgrade password hash for user {}", userId, e);
        }
    }
}
