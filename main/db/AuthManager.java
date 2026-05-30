package com.capstone.db;

import java.sql.*;

public class AuthManager {

    public boolean register(String username, String plainPassword) {
        if (username == null || username.isBlank() || plainPassword == null || plainPassword.isBlank()) return false;
        String hashed = BCrypt.hashpw(plainPassword, BCrypt.gensalt());
        String sql    = "INSERT INTO users (username, password) VALUES (?, ?)";
        try (Connection conn = DBConnection.get(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username.trim()); ps.setString(2, hashed); ps.executeUpdate(); return true;
        } catch (SQLIntegrityConstraintViolationException e) {
            return false;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public int login(String username, String plainPassword) {
        if (username == null || username.isBlank() || plainPassword == null || plainPassword.isBlank()) return -1;
        String sql = "SELECT id, password FROM users WHERE username = ?";
        try (Connection conn = DBConnection.get(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username.trim()); ResultSet rs = ps.executeQuery();
            if (rs.next() && BCrypt.checkpw(plainPassword, rs.getString("password"))) return rs.getInt("id");
        } catch (SQLException e) { e.printStackTrace(); }
        return -1;
    }
}
