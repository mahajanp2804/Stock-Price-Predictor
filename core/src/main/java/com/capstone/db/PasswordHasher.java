package com.capstone.db;

import at.favre.lib.crypto.bcrypt.BCrypt;

import java.nio.charset.StandardCharsets;

/**
 * Password hashing with standard bcrypt (at.favre.lib).
 *
 * Hashes made by the project's old hand-written implementation (51 chars) are
 * still accepted by verify(); needsRehash() flags them so they can be upgraded
 * on the next successful login.
 */
public final class PasswordHasher {

    static final int COST = 12;
    /** bcrypt only uses the first 72 bytes of a password; reject longer ones. */
    public static final int MAX_BYTES = 72;

    private static final int STANDARD_HASH_LENGTH = 60;

    private PasswordHasher() {}

    public static String hash(String password) {
        if (!isAcceptableLength(password)) throw new IllegalArgumentException("Password must be 1-72 bytes");
        return BCrypt.withDefaults().hashToString(COST, password.toCharArray());
    }

    @SuppressWarnings("deprecation")
    public static boolean verify(String password, String storedHash) {
        if (password == null || storedHash == null) return false;
        try {
            if (storedHash.length() == STANDARD_HASH_LENGTH) {
                return BCrypt.verifyer().verify(password.toCharArray(), storedHash).verified;
            }
            return LegacyBCrypt.checkpw(password, storedHash);
        } catch (RuntimeException e) {
            return false; // malformed hash or over-long password
        }
    }

    /** True for legacy hashes and for standard hashes below the current cost. */
    public static boolean needsRehash(String storedHash) {
        if (storedHash == null || storedHash.length() != STANDARD_HASH_LENGTH) return true;
        try {
            return Integer.parseInt(storedHash.substring(4, 6)) < COST;
        } catch (RuntimeException e) {
            return true;
        }
    }

    public static boolean isAcceptableLength(String password) {
        if (password == null || password.isEmpty()) return false;
        return password.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES;
    }
}
