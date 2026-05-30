package com.capstone.prediction;

public class Utils {
    private Utils() {}
    public static String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }
    public static String formatDollars(double amount) { return String.format("$%,.2f", amount); }
    public static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
}
