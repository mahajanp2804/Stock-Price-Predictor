package com.capstone.db;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuthManagerDbTest extends DbTestSupport {

    private final AuthManager auth = new AuthManager();

    @Test
    @DisplayName("register then log in; wrong password and unknown user fail")
    void registerAndLogin() throws Exception {
        assertEquals(AuthManager.RegisterResult.OK, auth.register("alice", "password1"));
        int id = auth.login("alice", "password1");
        assertTrue(id > 0);
        assertEquals(-1, auth.login("alice", "password2"));
        assertEquals(-1, auth.login("nobody", "password1"));
        assertTrue(scalar("SELECT password FROM users WHERE id=" + id).startsWith("$2a$12$"));
    }

    @Test
    @DisplayName("usernames are unique")
    void duplicate() {
        assertEquals(AuthManager.RegisterResult.OK, auth.register("bob", "password1"));
        assertEquals(AuthManager.RegisterResult.USERNAME_TAKEN, auth.register("bob", "other-password"));
    }

    @Test
    @DisplayName("blank or over-long credentials are rejected")
    void invalid() {
        assertEquals(AuthManager.RegisterResult.INVALID, auth.register(" ", "password1"));
        assertEquals(AuthManager.RegisterResult.INVALID, auth.register("carol", "x".repeat(73)));
    }

    @Test
    @DisplayName("an account with an old-style hash logs in and is upgraded to standard bcrypt")
    void legacyUpgrade() throws Exception {
        exec("INSERT INTO users(username, password) VALUES('legacy', '$2a$10$3lN9KvhmqfR/qBNqIU3Dc.kNQ6.zvFm.qstKH/9zXmke')");
        int id = auth.login("legacy", "secret123");
        assertTrue(id > 0);
        String upgraded = scalar("SELECT password FROM users WHERE id=" + id);
        assertEquals(60, upgraded.length());
        assertTrue(upgraded.startsWith("$2a$12$"));
        assertEquals(id, auth.login("legacy", "secret123"));
    }
}
