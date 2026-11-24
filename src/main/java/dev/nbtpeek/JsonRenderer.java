package dev.nbtpeek;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Turns the JSON form of a data component into compact, human readable "Label: value" entries.
 * No Minecraft classes in here on purpose, so it is easy to read and to test.
 */
final class JsonRenderer {
    /** value == null means "header line" (children follow). coord = coordinate triple. highlight = user-pinned key. */
    record Entry(int depth, String label, String value, boolean coord, boolean highlight) {}

    private static final Pattern ID = Pattern.compile("^(?:[a-z0-9_.-]+:)?[a-z0-9_./-]+$");
    private static final Pattern INT = Pattern.compile("-?\\d+");
    private static final Set<String> FLAG_WORDS = Set.of("has", "is", "can", "no", "from", "should", "will", "was",
            "did", "allow", "show", "enable", "persistence", "silent", "glowing", "powered", "lit", "locked", "sitting");
    private static final String[] GENERATIONS = {"Original", "Copy of original", "Copy of a copy", "Tattered"};
    static final String SEP = " \u203A "; // " > " between merged path parts

    private final PeekConfig cfg;
    private final View view;
    private final List<Entry> out;

    JsonRenderer(PeekConfig cfg, View view, List<Entry> out) {
        this.cfg = cfg;
        this.view = view;
        this.out = out;
    }

    /** fullId is the component id, e.g. "minecraft:custom_data". */
    void root(String fullId, JsonElement e) {
        String name = Fmt.strip(fullId);
        String label = label(name) + (cfg.showComponentIds ? " (" + fullId + ")" : "");

        if (name.equals("attribute_modifiers")) {
            attributes(label, e);
            return;
        }
        if ((cfg.containerSummary || view.all()) && (name.equals("container") || name.equals("bundle_contents"))) {
            container(label, name, e);
            return;
        }
        if (view.all()) {
            switch (name) {
                case "custom_name", "item_name" -> {
                    String t = Fmt.plain(e);
                    if (!t.isEmpty()) emit(0, label, clip(t), false);
                    return;
                }
                case "lore" -> {
                    lore(label, e);
                    return;
                }
                case "written_book_content", "writable_book_content" -> {
                    book(label, e);
                    return;
                }
                default -> { }
            }
        }
        render(label, name, e, 0);
    }

    // ---------------------------------------------------------------- generic

    private void render(String label, String key, JsonElement e, int depth) {
        if (e == null || e.isJsonNull()) return;

        if (e.isJsonArray()) {
            JsonArray arr = e.getAsJsonArray();
            if (key.equals("bees") && allObjects(arr)) {
                bees(label, arr, depth);
                return;
            }
            if (isEffectList(arr)) {
                effects(label, arr, depth);
                return;
            }
        }
        if (e.isJsonObject()) {
            JsonObject obj = e.getAsJsonObject();
            if (key.equals("profile") && profile(label, obj, depth)) return;
            if (isStack(obj)) {
                stack(label, obj, depth);
                return;
            }
        }

        if (e.isJsonPrimitive()) {
            emit(depth, label, prim(key, e.getAsJsonPrimitive()), false);
            return;
        }

        if (e.isJsonObject()) {
            JsonObject o = prune(e.getAsJsonObject());
            if (o.size() == 0) {
                if (view.all()) emit(depth, label, "(empty)", false);
                return;
            }
            if (depth > view.maxDepth()) {
                emit(depth, label, clip(Fmt.snbt(o)), false);
                return;
            }
            if (o.size() == 1) {
                Map.Entry<String, JsonElement> only = o.entrySet().iterator().next();
                render(label + SEP + label(only.getKey()), only.getKey(), only.getValue(), depth);
                return;
            }
            String inline = inline(o);
            if (inline != null && label.length() + 2 + inline.length() <= view.width()) {
                emit(depth, label, inline, false);
                return;
            }
            emit(depth, label, null, false);
            for (Map.Entry<String, JsonElement> en : o.entrySet()) {
                render(label(en.getKey()), en.getKey(), en.getValue(), depth + 1);
            }
            return;
        }

        JsonArray a = e.getAsJsonArray();
        if (a.size() == 0) {
            if (view.all()) emit(depth, label, "(empty)", false);
            return;
        }
        if (allPrimitive(a)) {
            emit(depth, label, primArray(key, a), isCoordKey(key) && isCoord(a));
            return;
        }
        int limit = view.listLimit();
        int n = a.size();
        for (int i = 0; i < Math.min(n, limit); i++) {
            render(n > 1 ? label + " #" + (i + 1) : label, key, a.get(i), depth);
        }
        if (n > limit) emit(depth, label, "+" + (n - limit) + " more", false);
    }

    /** "Label: value; Label: value" if every value is a simple leaf, else null. */
    private String inline(JsonObject o) {
        if (o.size() > 8) return null;
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, JsonElement> en : o.entrySet()) {
            JsonElement v = en.getValue();
            if (cfg.highlighted(en.getKey())) return null;   // a pinned key gets its own, colored line
            String s;
            if (v.isJsonPrimitive()) s = prim(en.getKey(), v.getAsJsonPrimitive());
            else if (v.isJsonArray() && v.getAsJsonArray().size() > 0 && allPrimitive(v.getAsJsonArray()))
                s = primArray(en.getKey(), v.getAsJsonArray());
            else return null;
            if (sb.length() > 0) sb.append("; ");
            sb.append(label(en.getKey())).append(": ").append(s);
        }
        return sb.toString();
    }

    /** Drops noise keys (unless ALL mode) and sorts keys: "id" first, then alphabetical. */
    JsonObject prune(JsonObject o) {
        TreeMap<String, JsonElement> sorted = null;
        if (cfg.sortKeys) {
            sorted = new TreeMap<>((x, y) -> {
                if (x.equals("id") != y.equals("id")) return x.equals("id") ? -1 : 1;
                int c = x.compareToIgnoreCase(y);
                return c != 0 ? c : x.compareTo(y);
            });
        }
        JsonObject r = new JsonObject();
        for (Map.Entry<String, JsonElement> en : o.entrySet()) {
            if (!view.all() && cfg.ignoredKey(en.getKey())) continue;
            if (sorted != null) sorted.put(en.getKey(), en.getValue());
            else r.add(en.getKey(), en.getValue());
        }
        if (sorted != null) for (Map.Entry<String, JsonElement> en : sorted.entrySet()) r.add(en.getKey(), en.getValue());
        return r;
    }

    /** Copy of a JSON tree with noise keys removed (used by the raw/SNBT layout). */
    static JsonElement pruneDeep(PeekConfig cfg, JsonElement e) {
        if (e instanceof JsonObject o) {
            JsonObject r = new JsonObject();
            for (Map.Entry<String, JsonElement> en : o.entrySet()) {
                if (!cfg.ignoredKey(en.getKey())) r.add(en.getKey(), pruneDeep(cfg, en.getValue()));
            }
            return r;
        }
        if (e instanceof JsonArray a) {
            JsonArray r = new JsonArray();
            for (JsonElement x : a) r.add(pruneDeep(cfg, x));
            return r;
        }
        return e;
    }

    private void emit(int depth, String label, String value, boolean coord) {
        String last = label.contains(SEP) ? label.substring(label.lastIndexOf(SEP) + SEP.length()) : label;
        out.add(new Entry(depth, label, value, coord, cfg.highlighted(last)));
    }

    // ---------------------------------------------------------------- item stacks / containers

    private static boolean isStack(JsonObject o) {
        if (!(o.get("id") instanceof JsonPrimitive idp) || !idp.isString()) return false;
        if (!(o.get("count") instanceof JsonPrimitive c) || !c.isNumber()) return false;
        for (String k : o.keySet()) if (!k.equals("id") && !k.equals("count") && !k.equals("components") && !k.equals("slot")) return false;
        return true;
    }

    /** "Diamond ×12" and, when expanded, the stack's own components below it. */
    private void stack(String label, JsonObject o, int depth) {
        Long c = asLong(o.get("count"));
        String v = value(o.get("id").getAsString()) + " \u00D7" + (c == null ? "?" : String.valueOf(c));
        JsonObject comps = o.get("components") instanceof JsonObject cc && cc.size() > 0 ? cc : null;
        if (comps != null && !view.expanded()) v += " (+data)";
        emit(depth, label, v, false);
        if (comps != null && view.expanded()) render("Data", "components", comps, depth + 1);
    }

    private void container(String label, String key, JsonElement e) {
        JsonArray arr = null;
        if (e instanceof JsonArray direct) arr = direct;
        else if (e instanceof JsonObject obj && obj.get("items") instanceof JsonArray listed) arr = listed;
        if (arr == null) {
            render(label, key, e, 0);
            return;
        }
        long stacks = 0, items = 0, withData = 0;
        Map<String, Long> totals = new LinkedHashMap<>();
        List<JsonObject> stackObjs = new ArrayList<>();
        List<Long> slots = new ArrayList<>();
        for (JsonElement el : arr) {
            if (!(el instanceof JsonObject o)) continue;
            JsonObject it = o.get("item") instanceof JsonObject inner ? inner : o;
            if (!(it.get("id") instanceof JsonPrimitive idp)) continue;
            Long c = asLong(it.get("count"));
            long count = c == null ? 1 : c;
            stacks++;
            items += count;
            totals.merge(idp.getAsString(), count, Long::sum);
            stackObjs.add(it);
            slots.add(asLong(o.get("slot")));
            if (it.get("components") instanceof JsonObject comps) {
                for (String k : comps.keySet()) {
                    if (k.contains("custom_data")) {
                        withData++;
                        break;
                    }
                }
            }
        }
        if (stacks == 0) {
            if (view.all()) emit(0, label, "(empty)", false);
            return;
        }

        emit(0, label, stacks + (stacks == 1 ? " stack, " : " stacks, ")
                + Fmt.group(items) + (items == 1 ? " item" : " items"), false);

        List<Map.Entry<String, Long>> sorted = new ArrayList<>(totals.entrySet());
        sorted.sort((x, y) -> Long.compare(y.getValue(), x.getValue()));
        int limit = view.all() ? Integer.MAX_VALUE : view.expanded() ? 6 : 3;
        StringBuilder top = new StringBuilder();
        for (int i = 0; i < Math.min(limit, sorted.size()); i++) {
            if (i > 0) top.append(", ");
            top.append(value(sorted.get(i).getKey())).append(" \u00D7").append(Fmt.group(sorted.get(i).getValue()));
        }
        if (sorted.size() > limit) top.append(", +").append(sorted.size() - limit).append(" more");
        emit(1, "Most", top.toString(), false);
        if (withData > 0) emit(1, "With custom data", withData + (withData == 1 ? " stack" : " stacks"), false);

        if (view.all()) {   // ALL mode: every stack, with its own data
            for (int i = 0; i < stackObjs.size(); i++) {
                String slot = slots.get(i) != null ? "Slot " + slots.get(i) : "Item " + (i + 1);
                JsonObject it = stackObjs.get(i);
                if (isStack(it)) stack(slot, it, 1);
                else render(slot, "item", it, 1);
            }
        }
    }

    // ---------------------------------------------------------------- text (names, lore, books)

    private void lore(String label, JsonElement e) {
        if (!(e instanceof JsonArray a) || a.size() == 0) return;
        emit(0, label, null, false);
        for (int i = 0; i < a.size(); i++) emit(1, "Line " + (i + 1), clip(Fmt.plain(a.get(i))), false);
    }

    private void book(String label, JsonElement e) {
        if (!(e instanceof JsonObject o)) {
            render(label, "book", e, 0);
            return;
        }
        emit(0, label, null, false);
        String title = o.get("title") == null ? "" : Fmt.plain(o.get("title"));
        if (!title.isEmpty()) emit(1, "Title", clip(title), false);
        if (o.get("author") instanceof JsonPrimitive au) emit(1, "Author", clip(au.getAsString()), false);
        Long gen = asLong(o.get("generation"));
        if (gen != null && gen >= 0 && gen < GENERATIONS.length) emit(1, "Generation", GENERATIONS[(int) (long) gen], false);
        if (o.get("pages") instanceof JsonArray pages) {
            emit(1, "Pages", String.valueOf(pages.size()), false);
            for (int i = 0; i < Math.min(pages.size(), view.listLimit()); i++) {
                String t = Fmt.plain(pages.get(i));
                emit(1, "Page " + (i + 1), t.isEmpty() ? "(blank)" : clip(t), false);
            }
        }
    }

    // ---------------------------------------------------------------- effects (potions, stew, food)

    private static boolean isEffectList(JsonArray a) {
        if (!allObjects(a)) return false;
        JsonObject f = a.get(0).getAsJsonObject();
        return f.get("id") instanceof JsonPrimitive && (f.has("amplifier") || f.has("duration"));
    }

    private void effects(String label, JsonArray a, int depth) {
        int limit = view.listLimit();
        if (a.size() == 1) {
            effect(label + SEP + effectName(a.get(0).getAsJsonObject()), a.get(0).getAsJsonObject(), depth);
            return;
        }
        emit(depth, label, null, false);
        for (int i = 0; i < Math.min(a.size(), limit); i++) {
            JsonObject o = a.get(i).getAsJsonObject();
            effect(effectName(o), o, depth + 1);
        }
        if (a.size() > limit) emit(depth + 1, label, "+" + (a.size() - limit) + " more", false);
    }

    private String effectName(JsonObject o) {
        return value(str(o.get("id")));
    }

    private void effect(String label, JsonObject o, int depth) {
        StringBuilder sb = new StringBuilder();
        Long amp = asLong(o.get("amplifier"));
        sb.append("Level ").append(Fmt.roman((amp == null ? 0 : amp) + 1));
        Long dur = asLong(o.get("duration"));
        if (dur != null) sb.append(" \u00B7 ").append(dur < 0 ? "infinite" : Fmt.duration(dur));
        emit(depth, label, sb.toString(), false);
    }

    // ---------------------------------------------------------------- player heads

    private boolean profile(String label, JsonObject o, int depth) {
        List<String> parts = new ArrayList<>();
        if (o.get("name") instanceof JsonPrimitive n && !n.getAsString().isEmpty()) parts.add("Name: " + n.getAsString());
        if (o.get("id") instanceof JsonArray ida && ida.size() == 4 && allInts(ida)) parts.add("UUID: " + Fmt.uuid(ida));
        else if (o.get("id") instanceof JsonPrimitive idp) parts.add("UUID: " + Fmt.shorten(idp.getAsString(), 8));
        JsonElement props = o.get("properties");
        if (props != null && !props.isJsonNull()) {
            String skin = findSkin(props, 0);
            parts.add(skin != null ? "Skin: " + skin : "Custom skin data");
        }
        if (parts.isEmpty()) return false;
        emit(depth, label, String.join("; ", parts), false);
        return true;
    }

    /** Finds a base64 "textures" blob anywhere under a profile's properties and returns a short skin id. */
    private static String findSkin(JsonElement e, int depth) {
        if (depth > 4) return null;
        if (e instanceof JsonPrimitive p && p.isString() && p.getAsString().length() > 20) {
            try {
                String json = new String(Base64.getDecoder().decode(p.getAsString()), StandardCharsets.UTF_8);
                JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                String url = root.getAsJsonObject("textures").getAsJsonObject("SKIN").get("url").getAsString();
                return Fmt.shorten(url.substring(url.lastIndexOf('/') + 1), 10);
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        if (e instanceof JsonArray a) {
            for (JsonElement x : a) {
                String r = findSkin(x, depth + 1);
                if (r != null) return r;
            }
        } else if (e instanceof JsonObject o) {
            for (Map.Entry<String, JsonElement> en : o.entrySet()) {
                String r = findSkin(en.getValue(), depth + 1);
                if (r != null) return r;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- bees

    private void bees(String label, JsonArray a, int depth) {
        emit(depth, label, String.valueOf(a.size()), false);
        int limit = view.listLimit();
        for (int i = 0; i < Math.min(a.size(), limit); i++) {
            JsonObject b = a.get(i).getAsJsonObject();
            JsonObject ed = b.get("entity_data") instanceof JsonObject x ? x : b;

            StringBuilder where = new StringBuilder();
            add(where, "Hive", coords(ed.get("hive_pos")));
            add(where, "Flower", coords(ed.get("flower_pos")));

            StringBuilder status = new StringBuilder();
            Long t = asLong(b.get("ticks_in_hive"));
            Long m = asLong(b.get("min_ticks_in_hive"));
            if (t != null && m != null) add(status, null, "in hive " + Fmt.duration(t) + " of " + Fmt.duration(m));
            else if (t != null) add(status, null, "in hive " + Fmt.duration(t));
            if (truthy(ed.get("HasNectar"))) add(status, null, "carrying nectar");
            if (truthy(ed.get("HasStung"))) add(status, null, "has stung");

            String name = "Bee " + (i + 1);
            if (where.length() > 0) {
                emit(depth + 1, name, where.toString(), false);
                if (status.length() > 0) emit(depth + 2, "Status", status.toString(), false);
            } else {
                emit(depth + 1, name, status.length() > 0 ? status.toString() : "(no extra data)", false);
            }
        }
        if (a.size() > limit) emit(depth + 1, "Bees", "+" + (a.size() - limit) + " more", false);
    }

    private static void add(StringBuilder sb, String key, String val) {
        if (val == null) return;
        if (sb.length() > 0) sb.append("; ");
        if (key != null) sb.append(key).append(": ");
        sb.append(val);
    }

    private String coords(JsonElement e) {
        return e instanceof JsonArray a && isCoord(a) ? xyz(a) : null;
    }

    private static boolean truthy(JsonElement e) {
        if (!(e instanceof JsonPrimitive p)) return false;
        if (p.isBoolean()) return p.getAsBoolean();
        if (p.isNumber()) return p.getAsDouble() != 0;
        return false;
    }

    private static Long asLong(JsonElement e) {
        if (e instanceof JsonPrimitive p && p.isNumber() && INT.matcher(p.getAsString()).matches()) {
            try {
                return Long.parseLong(p.getAsString());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- attributes

    private void attributes(String label, JsonElement e) {
        JsonArray arr = null;
        if (e instanceof JsonArray direct) arr = direct;
        else if (e instanceof JsonObject obj && obj.get("modifiers") instanceof JsonArray listed) arr = listed;
        if (arr == null || arr.size() == 0) {
            render(label, "attribute_modifiers", e, 0);
            return;
        }
        emit(0, label, null, false);
        int limit = view.all() ? Integer.MAX_VALUE : cfg.maxListEntries * (view.expanded() ? 4 : 2);
        int n = arr.size();
        for (int i = 0; i < Math.min(n, limit); i++) {
            if (!(arr.get(i) instanceof JsonObject m)) continue;
            String type = str(m.get("type"));
            type = type.substring(type.lastIndexOf(':') + 1);          // minecraft:generic.attack_damage
            type = type.substring(type.lastIndexOf('.') + 1);          // -> attack_damage
            String op = m.has("operation") ? str(m.get("operation")) : "add_value";
            String slot = m.has("slot") ? str(m.get("slot")) : "any";
            String amount = "?";
            if (m.get("amount") instanceof JsonPrimitive p && p.isNumber()) {
                double d = p.getAsDouble();
                amount = switch (Fmt.strip(op)) {
                    case "add_multiplied_base" -> Fmt.signed(d * 100) + "% (of base)";
                    case "add_multiplied_total" -> Fmt.signed(d * 100) + "% (total multiplier)";
                    default -> Fmt.signed(d);
                };
            }
            String text = amount + (slot.equals("any") ? "" : " \u00B7 " + slotName(slot));
            if (view.expanded() && m.has("id")) text += " \u00B7 id " + Fmt.strip(str(m.get("id")));
            emit(1, label(type), text, false);
        }
        if (n > limit) emit(1, "Attributes", "+" + (n - limit) + " more", false);
    }

    private String slotName(String slot) {
        if (!cfg.readable) return slot;
        return switch (Fmt.strip(slot)) {
            case "mainhand" -> "Main Hand";
            case "offhand" -> "Off Hand";
            case "hand" -> "Either Hand";
            case "head" -> "Head";
            case "chest" -> "Chest";
            case "legs" -> "Legs";
            case "feet" -> "Feet";
            case "body" -> "Body";
            case "armor" -> "Any Armor";
            default -> Fmt.pretty(slot);
        };
    }

    private static String str(JsonElement e) {
        return e instanceof JsonPrimitive p ? p.getAsString() : "?";
    }

    // ---------------------------------------------------------------- value formatting

    private String prim(String key, JsonPrimitive p) {
        if (p.isBoolean()) return cfg.readable ? (p.getAsBoolean() ? "Yes" : "No") : String.valueOf(p.getAsBoolean());
        if (!p.isNumber()) return clip(value(p.getAsString()));

        String raw = p.getAsString();
        boolean isInt = INT.matcher(raw).matches();
        if (cfg.readable) {
            String lk = key == null ? "" : key.toLowerCase(Locale.ROOT);
            if (isInt && (raw.equals("0") || raw.equals("1")) && isFlagKey(key)) return raw.equals("1") ? "Yes" : "No";
            if (isInt && lk.equals("amplifier")) return Fmt.roman(Long.parseLong(raw) + 1);
            if (isInt && lk.contains("duration") && raw.equals("-1")) return "Infinite";
            if (isInt && isTimeKey(key)) {
                try {
                    return Fmt.ticks(Long.parseLong(raw));
                } catch (NumberFormatException ignored) {
                    // fall through to plain number
                }
            }
            if (isInt && (lk.contains("color") || lk.contains("colour") || lk.equals("rgb"))) {
                try {
                    return Fmt.hexColor(Long.parseLong(raw));
                } catch (NumberFormatException ignored) {
                    // fall through
                }
            }
            if ((lk.contains("chance") || lk.contains("probability")) && p.getAsDouble() >= 0 && p.getAsDouble() <= 1) {
                return Fmt.fmt(p.getAsDouble() * 100) + "%";
            }
            if (!isInt && isAngleKey(lk)) return Fmt.fmt(p.getAsDouble()) + "\u00B0";
            if (isInt && cfg.groupNumbers && raw.replace("-", "").length() >= 5 && !isIdLike(lk)) {
                try {
                    return Fmt.group(Long.parseLong(raw));
                } catch (NumberFormatException ignored) {
                    // too large for long: show as is
                }
            }
        }
        return isInt ? raw : Fmt.fmt(p.getAsDouble());
    }

    private String primArray(String key, JsonArray a) {
        if (isCoordKey(key) && isCoord(a)) return xyz(a);
        if (a.size() == 4 && isUuidKey(key) && allInts(a)) return Fmt.uuid(a);
        StringBuilder sb = new StringBuilder();
        int max = view.all() ? 64 : 8;
        for (int i = 0; i < Math.min(a.size(), max); i++) {
            if (i > 0) sb.append(", ");
            sb.append(prim(key, a.get(i).getAsJsonPrimitive()));
        }
        if (a.size() > max) sb.append(" \u2026 (").append(a.size()).append(" total)");
        return sb.toString();
    }

    private String xyz(JsonArray a) {
        String x = prim(null, a.get(0).getAsJsonPrimitive());
        String y = prim(null, a.get(1).getAsJsonPrimitive());
        String z = prim(null, a.get(2).getAsJsonPrimitive());
        return cfg.readable ? "X " + x + ", Y " + y + ", Z " + z : "(" + x + ", " + y + ", " + z + ")";
    }

    private static boolean isCoordKey(String key) {
        if (key == null) return false;
        String k = key.toLowerCase(Locale.ROOT);
        return k.contains("pos") || k.contains("location") || k.contains("coord") || k.contains("spawn")
                || k.contains("home") || k.equals("target") || k.equals("center");
    }

    private static boolean isUuidKey(String key) {
        if (key == null) return false;
        String k = key.toLowerCase(Locale.ROOT);
        return k.contains("uuid") || k.contains("owner") || k.contains("thrower") || k.contains("trusted");
    }

    /** Keys like HasNectar, isShiny, NoAI, FromBucket hold 0/1 flags. */
    private static boolean isFlagKey(String key) {
        if (key == null) return false;
        String first = Fmt.pretty(key).split(" ")[0].toLowerCase(Locale.ROOT);
        return FLAG_WORDS.contains(first);
    }

    private static boolean isTimeKey(String key) {
        if (key == null) return false;
        String k = key.toLowerCase(Locale.ROOT);
        return k.contains("tick") || k.contains("duration") || k.endsWith("time") || k.endsWith("cooldown");
    }

    private static boolean isAngleKey(String lk) {
        return lk.equals("yaw") || lk.equals("pitch") || lk.equals("roll") || lk.endsWith("yaw") || lk.endsWith("pitch");
    }

    /** Seeds, hashes, ids: big numbers that should not get thousands separators. */
    private static boolean isIdLike(String lk) {
        return lk.equals("id") || lk.endsWith("_id") || lk.endsWith("id") && lk.length() <= 8
                || lk.contains("seed") || lk.contains("hash") || lk.contains("uuid");
    }

    // ---------------------------------------------------------------- naming

    private String label(String key) {
        return cfg.readable ? Fmt.pretty(key) : Fmt.strip(key);
    }

    /** "minecraft:overworld" -> "Overworld"; "add_value" -> "Add Value"; ordinary text is untouched. */
    private String value(String s) {
        if (!cfg.readable) return Fmt.strip(s);
        if (ID.matcher(s).matches() && (s.contains(":") || s.contains("_"))) {
            int c = s.indexOf(':');
            String ns = c >= 0 ? s.substring(0, c) : null;
            String path = c >= 0 ? s.substring(c + 1) : s;
            String r = Fmt.pretty(path);
            return ns != null && !ns.equals("minecraft") ? r + " (" + ns + ")" : r;
        }
        return Fmt.strip(s);
    }

    // ---------------------------------------------------------------- helpers

    private static boolean allObjects(JsonArray a) {
        if (a.size() == 0) return false;
        for (JsonElement x : a) if (!x.isJsonObject()) return false;
        return true;
    }

    private static boolean allPrimitive(JsonArray a) {
        for (JsonElement x : a) if (!x.isJsonPrimitive()) return false;
        return true;
    }

    private static boolean isCoord(JsonArray a) {
        if (a.size() != 3) return false;
        for (JsonElement x : a) if (!(x.isJsonPrimitive() && x.getAsJsonPrimitive().isNumber())) return false;
        return true;
    }

    private static boolean allInts(JsonArray a) {
        for (JsonElement x : a) {
            if (!(x.isJsonPrimitive() && x.getAsJsonPrimitive().isNumber()) || !INT.matcher(x.getAsString()).matches())
                return false;
        }
        return true;
    }

    private String clip(String s) {
        int max = view.width();
        return s.length() > max ? s.substring(0, max - 1) + "\u2026" : s;
    }
}
