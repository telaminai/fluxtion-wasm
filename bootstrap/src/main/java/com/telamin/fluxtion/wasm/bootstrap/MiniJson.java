package com.telamin.fluxtion.wasm.bootstrap;

/**
 * A minimal, explicit, reflection-free JSON field reader — enough for the value
 * bridge to pull a {@code "type"} discriminator and primitive fields out of a flat
 * JSON object string. TeaVM-clean (no Jackson/Gson, no reflection, no regex state).
 *
 * <p>Deliberately tiny: it reads flat {@code {"k":"v","n":1}} objects, which is the
 * shape the JS bridge sends. Structured decoding of a specific event belongs in a
 * graph decoder node, not here.
 */
public final class MiniJson {

    private MiniJson() {
    }

    /** Extract the raw string value for {@code key}, or {@code null} if absent. */
    public static String string(String json, String key) {
        if (json == null) {
            return null;
        }
        String token = "\"" + key + "\"";
        int from = 0;
        while (true) {
            int i = json.indexOf(token, from);
            if (i < 0) {
                return null;
            }
            // only a KEY if the next non-space char is ':' — otherwise this is a value
            // (e.g. searching "price" must skip the "type":"price" value), so keep looking.
            int c = i + token.length();
            while (c < json.length() && json.charAt(c) == ' ') {
                c++;
            }
            if (c >= json.length() || json.charAt(c) != ':') {
                from = i + token.length();
                continue;
            }
            int start = c + 1;
            while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '"')) {
                start++;
            }
            int end = start;
            while (end < json.length()
                    && json.charAt(end) != '"' && json.charAt(end) != ',' && json.charAt(end) != '}') {
                end++;
            }
            return start <= end ? json.substring(start, end).trim() : null;
        }
    }

    /** Extract an int field, or {@code defaultValue} if absent/unparseable. */
    public static int intField(String json, String key, int defaultValue) {
        String s = string(json, key);
        if (s == null || s.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /** Extract a long field, or {@code defaultValue} if absent/unparseable. */
    public static long longField(String json, String key, long defaultValue) {
        String s = string(json, key);
        if (s == null || s.isEmpty()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /** Extract a double field, or {@code defaultValue} if absent/unparseable. */
    public static double doubleField(String json, String key, double defaultValue) {
        String s = string(json, key);
        if (s == null || s.isEmpty()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /** Extract a boolean field ({@code true}/{@code false}), or {@code defaultValue} if absent. */
    public static boolean boolField(String json, String key, boolean defaultValue) {
        String s = string(json, key);
        if (s == null || s.isEmpty()) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(s) || "1".equals(s);
    }
}
