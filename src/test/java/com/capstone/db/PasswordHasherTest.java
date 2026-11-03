package com.capstone.db;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordHasherTest {

    /** Made by the project's original hand-written BCrypt for "secret123" (51 chars, non-standard). */
    private static final String LEGACY_HASH = "$2a$10$3lN9KvhmqfR/qBNqIU3Dc.kNQ6.zvFm.qstKH/9zXmke";

    @Test
    @DisplayName("hash then verify round-trips; wrong password fails")
    void roundTrip() {
        String h = PasswordHasher.hash("correct horse");
        assertEquals(60, h.length());
        assertTrue(PasswordHasher.verify("correct horse", h));
        assertFalse(PasswordHasher.verify("wrong horse", h));
    }

    @Test
    @DisplayName("hashes are salted — same password gives different hashes")
    void salted() {
        assertNotEquals(PasswordHasher.hash("same"), PasswordHasher.hash("same"));
    }

    @Test
    @DisplayName("accounts created with the old implementation can still log in")
    void legacyHashVerifies() {
        assertTrue(PasswordHasher.verify("secret123", LEGACY_HASH));
        assertFalse(PasswordHasher.verify("secret124", LEGACY_HASH));
    }

    @Test
    @DisplayName("legacy and low-cost hashes need rehash; current ones don't")
    void needsRehash() {
        assertTrue(PasswordHasher.needsRehash(LEGACY_HASH));
        assertFalse(PasswordHasher.needsRehash(PasswordHasher.hash("pw")));
        String cost10 = at.favre.lib.crypto.bcrypt.BCrypt.withDefaults().hashToString(10, "pw".toCharArray());
        assertTrue(PasswordHasher.needsRehash(cost10));
        assertTrue(PasswordHasher.verify("pw", cost10));
    }

    @Test
    @DisplayName("passwords over 72 bytes are rejected")
    void lengthLimit() {
        assertTrue(PasswordHasher.isAcceptableLength("a".repeat(72)));
        assertFalse(PasswordHasher.isAcceptableLength("a".repeat(73)));
        assertFalse(PasswordHasher.isAcceptableLength(""));
        assertThrows(IllegalArgumentException.class, () -> PasswordHasher.hash("a".repeat(73)));
    }

    @Test
    @DisplayName("malformed or missing hashes never verify")
    void malformed() {
        assertFalse(PasswordHasher.verify("pw", "not-a-hash"));
        assertFalse(PasswordHasher.verify("pw", null));
        assertFalse(PasswordHasher.verify(null, LEGACY_HASH));
    }
}
