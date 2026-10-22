package com.neha.VideoStreamApp.helper;

import org.springframework.data.domain.KeysetScrollPosition;
import org.springframework.data.domain.ScrollPosition;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ScrollPosition <-> string codec.
 *
 * Keyset pagination ka position encode/decode karta hai. Pehle ka format
 * "key1=value1,key2=value2" tha jisme:
 *   - value me "," ya "=" ho (jaise video title "Part 1, Episode = 2") to
 *     decode galat ho jata tha aur pairs silently drop ho jate the
 *     -> rows skip/duplicate ho jate the
 *   - decode har value ko pehle Instant, phir Long, phir String try karta tha
 *     -> ek title "2024" ya "12345" NUMERIC me convert ho jata tha aur keyset
 *        comparison varchar column ke against mismatch kar deta tha
 *   - corrupt scrollId pe silently page 1 return hota tha -> client
 *     "load more" loop me infinite chala sakta tha
 *
 * Ab format JSON hai aur har value ka apna type tag hai.
 */
public class ScrollPositionCodec {

    private ScrollPositionCodec() {
    }

    // Encode ScrollPosition to Base64 string
    public static String encode(ScrollPosition position) {
        if (position == null || position.isInitial()) {
            return null;
        }

        if (position instanceof KeysetScrollPosition keysetPosition) {
            Map<String, Object> keys = keysetPosition.getKeys();

            Map<String, String> serializable = new LinkedHashMap<>();
            keys.forEach((key, value) -> {
                if (value != null) {
                    serializable.put(key, encodeValue(value));
                }
            });

            if (serializable.isEmpty()) {
                return null;
            }

            // Simple manual JSON (dependency-free) — keys escaped properly
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, String> entry : serializable.entrySet()) {
                if (!first) {
                    sb.append(",");
                }
                first = false;
                sb.append("\"").append(escapeJson(entry.getKey())).append("\":");
                sb.append("\"").append(escapeJson(entry.getValue())).append("\"");
            }
            sb.append("}");

            return Base64.getEncoder().encodeToString(sb.toString().getBytes(StandardCharsets.UTF_8));
        }

        return null;
    }

    // Value ko type-tag ke saath encode karo: "i:<epochMillis>", "s:<string>", "l:<long>"
    private static String encodeValue(Object value) {
        if (value instanceof Instant instant) {
            return "i:" + instant.toEpochMilli();
        }
        if (value instanceof Number number) {
            return "l:" + number.longValue();
        }
        if (value instanceof Boolean bool) {
            return "b:" + bool;
        }
        return "s:" + String.valueOf(value);
    }

    // Decode Base64 string to ScrollPosition
    public static ScrollPosition decode(String encodedPosition) {
        if (encodedPosition == null || encodedPosition.isBlank()) {
            return ScrollPosition.keyset();
        }

        try {
            String decoded = new String(Base64.getDecoder().decode(encodedPosition), StandardCharsets.UTF_8);
            Map<String, Object> keys = parseJsonObject(decoded);

            if (keys.isEmpty()) {
                // Corrupt payload — page 1 pe wapas jaane se behtar hai 400 dena,
                // warna client "load more" loop me phir se page 1 maangta rehta hai.
                throw new IllegalArgumentException("Invalid scroll position");
            }

            return ScrollPosition.forward(keys);
        } catch (Exception e) {
            // Corrupt/expired scrollId → invalid signal. Callers ko isse handle
            // karna chahiye (400 return karein), page 1 silently na de.
            throw new IllegalArgumentException("Invalid scroll position", e);
        }
    }

    // Minimal JSON object parser — sirf string values (hamara format) support karta hai
    private static Map<String, Object> parseJsonObject(String json) {
        Map<String, Object> result = new LinkedHashMap<>();
        String trimmed = json.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            throw new IllegalArgumentException("Invalid scroll position format");
        }
        String body = trimmed.substring(1, trimmed.length() - 1);
        if (body.isBlank()) {
            return result;
        }

        int i = 0;
        while (i < body.length()) {
            // key
            int keyStart = i + 1;
            int keyEnd = body.indexOf('"', keyStart);
            if (keyEnd < 0) {
                throw new IllegalArgumentException("Invalid scroll position format");
            }
            String key = unescapeJson(body.substring(keyStart, keyEnd));

            int colon = body.indexOf(':', keyEnd);
            if (colon < 0) {
                throw new IllegalArgumentException("Invalid scroll position format");
            }

            int valStart = body.indexOf('"', colon);
            if (valStart < 0) {
                throw new IllegalArgumentException("Invalid scroll position format");
            }
            int valEnd = body.indexOf('"', valStart + 1);
            if (valEnd < 0) {
                throw new IllegalArgumentException("Invalid scroll position format");
            }
            String rawValue = unescapeJson(body.substring(valStart + 1, valEnd));

            result.put(key, decodeValue(rawValue));

            // next entry
            int nextComma = body.indexOf(',', valEnd);
            if (nextComma < 0) {
                break;
            }
            i = nextComma + 1;
        }
        return result;
    }

    // Type tag ke basis par sahi type me convert karo — guess nahi karte
    private static Object decodeValue(String raw) {
        if (raw == null) {
            return null;
        }
        if (raw.startsWith("i:")) {
            return Instant.ofEpochMilli(Long.parseLong(raw.substring(2)));
        }
        if (raw.startsWith("l:")) {
            return Long.parseLong(raw.substring(2));
        }
        if (raw.startsWith("b:")) {
            return Boolean.parseBoolean(raw.substring(2));
        }
        if (raw.startsWith("s:")) {
            return raw.substring(2);
        }
        // Legacy format (type tag ke bina) — backward compatibility
        return raw;
    }

    private static String escapeJson(String value) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    private static String unescapeJson(String value) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' && i + 1 < value.length()) {
                char next = value.charAt(++i);
                switch (next) {
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        if (i + 4 < value.length()) {
                            String hex = value.substring(i + 1, i + 5);
                            sb.append((char) Integer.parseInt(hex, 16));
                            i += 4;
                        }
                    }
                    default -> sb.append(next);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
