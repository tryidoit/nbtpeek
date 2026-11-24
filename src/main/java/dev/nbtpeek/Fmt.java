package dev.nbtpeek;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Pure text helpers (no Minecraft classes): naming, numbers, durations, text components, SNBT. */
final class Fmt {
    private static final Set<String> SMALL_WORDS = Set.of("in", "of", "to", "the", "a", "an", "and", "or", "on", "at", "for");
    private static final Pattern SIMPLE_KEY = Pattern.compile("[A-Za-z0-9_.+-]+");
    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    private Fmt() {}

    static String strip(String s) {
        return s.startsWith("minecraft:") ? s.substring(10) : s;
    }

    /** snake_case / camelCase / namespaced id -> "Title Case Words". */
    static String pretty(String key) {
        String s = strip(key);
        s = s.replace('_', ' ').replace('-', ' ').replace('.', ' ').replace('/', ' ');
        s = s.replaceAll("([a-z0-9])([A-Z])", "$1 $2").trim();
        if (s.isEmpty()) return "?";
        StringBuilder sb = new StringBuilder();
        String[] words = s.split("\\s+");
        for (int i = 0; i < words.length; i++) {
            String w = words[i];
            String lower = w.toLowerCase(Locale.ROOT);
            String r = switch (lower) {
                case "pos" -> "Position";
                case "id" -> "ID";
                case "uuid" -> "UUID";
                case "xp" -> "XP";
                case "hp" -> "HP";
                case "nbt" -> "NBT";
                case "ai" -> "AI";
                default -> (i > 0 && SMALL_WORDS.contains(lower))
                        ? lower
                        : Character.toUpperCase(w.charAt(0)) + w.substring(1);
            };
            if (i > 0) sb.append(' ');
            sb.append(r);
        }
        return sb.toString();
    }

    /** Normal form used to compare a configured key with a displayed label: "hive_pos" == "Hive Position". */
    static String norm(String s) {
        return pretty(s).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    static String fmt(double d) {
        if (d == Math.rint(d) && Math.abs(d) < 1e15) return Long.toString((long) d);
        if (Math.abs(d) < 0.001 || Math.abs(d) >= 1e15) return Double.toString(d);
        String s = String.format(Locale.ROOT, "%.3f", d);
        return s.replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    static String signed(double d) {
        return (d >= 0 ? "+" : "") + fmt(d);
    }

    /** 1234567 -> "1,234,567". */
    static String group(long v) {
        return String.format(Locale.ROOT, "%,d", v);
    }

    /** "30s (600 ticks)". */
    static String ticks(long t) {
        if (t < 20) return t + (t == 1 ? " tick" : " ticks");
        return duration(t) + " (" + t + " ticks)";
    }

    /** "1m 30s" from ticks. */
    static String duration(long t) {
        if (t <= 0) return "0s";
        if (t < 20) return "<1s";
        long s = t / 20;
        if (s >= 3600) {
            long m = (s % 3600) / 60;
            return (s / 3600) + "h" + (m == 0 ? "" : " " + m + "m");
        }
        if (s >= 60) return (s / 60) + "m" + (s % 60 == 0 ? "" : " " + (s % 60) + "s");
        return s + "s";
    }

    /** 1 -> I, 2 -> II ... 10 -> X, otherwise the number. */
    static String roman(long n) {
        return n >= 1 && n <= 10 ? ROMAN[(int) n] : String.valueOf(n);
    }

    /** 16711680 -> "#FF0000"; values with alpha -> "#AARRGGBB". */
    static String hexColor(long v) {
        if (v < 0) v &= 0xFFFFFFFFL;
        return v > 0xFFFFFFL ? String.format(Locale.ROOT, "#%08X", v) : String.format(Locale.ROOT, "#%06X", v);
    }

    static String shorten(String s, int n) {
        return s.length() > n ? s.substring(0, n) + "\u2026" : s;
    }

    static String uuid(JsonArray a) {
        long hi = ((long) a.get(0).getAsInt() << 32) | (a.get(1).getAsInt() & 0xFFFFFFFFL);
        long lo = ((long) a.get(2).getAsInt() << 32) | (a.get(3).getAsInt() & 0xFFFFFFFFL);
        return new UUID(hi, lo).toString().substring(0, 8) + "\u2026";
    }

    static String bytes(long n) {
        if (n < 1024) return n + " B";
        return String.format(Locale.ROOT, "%.1f KB", n / 1024.0);
    }

    // ------------------------------------------------------------------ text components

    /** Flattens a text component (string, {"text":..}, lists, stringified JSON, book pages {"raw":..}) to plain text. */
    static String plain(JsonElement e) {
        StringBuilder sb = new StringBuilder();
        plain(e, sb, 0);
        return sb.toString().replaceAll("\\s+", " ").trim();
    }

    private static void plain(JsonElement e, StringBuilder sb, int depth) {
        if (e == null || depth > 8) return;
        if (e instanceof JsonPrimitive p) {
            String s = p.getAsString();
            String t = s.trim();
            if ((t.startsWith("{") && t.endsWith("}")) || (t.startsWith("[") && t.endsWith("]"))) {
                try {
                    plain(JsonParser.parseString(t), sb, depth + 1);
                    return;
                } catch (RuntimeException ignored) {
                    // not JSON after all: fall through and print it as text
                }
            }
            sb.append(s);
        } else if (e instanceof JsonArray a) {
            for (JsonElement x : a) plain(x, sb, depth + 1);
        } else if (e instanceof JsonObject o) {
            if (o.get("raw") != null) {
                plain(o.get("raw"), sb, depth + 1);
                return;
            }
            if (o.get("text") instanceof JsonPrimitive tp) sb.append(tp.getAsString());
            else if (o.get("translate") instanceof JsonPrimitive tr) sb.append(tr.getAsString());
            if (o.get("extra") != null) plain(o.get("extra"), sb, depth + 1);
        }
    }

    // ------------------------------------------------------------------ SNBT-like text

    /** {"a":1,"b":[1,2]} -> {a:1,b:[1,2]}  (what /data get looks like, minus the type suffixes). */
    static String snbt(JsonElement e) {
        StringBuilder sb = new StringBuilder();
        snbt(e, sb);
        return sb.toString();
    }

    private static void snbt(JsonElement e, StringBuilder sb) {
        if (e == null || e.isJsonNull()) {
            sb.append("null");
        } else if (e instanceof JsonPrimitive p) {
            if (p.isString()) quote(p.getAsString(), sb);
            else sb.append(p.getAsString());
        } else if (e instanceof JsonArray a) {
            sb.append('[');
            for (int i = 0; i < a.size(); i++) {
                if (i > 0) sb.append(',');
                snbt(a.get(i), sb);
            }
            sb.append(']');
        } else if (e instanceof JsonObject o) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<String, JsonElement> en : o.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                if (SIMPLE_KEY.matcher(en.getKey()).matches()) sb.append(en.getKey());
                else quote(en.getKey(), sb);
                sb.append(':');
                snbt(en.getValue(), sb);
            }
            sb.append('}');
        }
    }

    private static void quote(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\\') sb.append('\\');
            sb.append(c);
        }
        sb.append('"');
    }
}
