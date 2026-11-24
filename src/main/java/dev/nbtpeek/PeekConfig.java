package dev.nbtpeek;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Settings. Live in config/nbtpeek.json, can be edited by hand or through the Mod Menu settings screen.
 * Never overwritten at startup once the file exists.
 */
public final class PeekConfig {
    private static final Logger LOG = LoggerFactory.getLogger("nbtpeek");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // ------------------------------------------------------------------ general
    public boolean enabled = true;

    /** Last preset applied from the settings screen (informational): minimal, balanced, detailed, everything. */
    public String preset = "balanced";

    /** "tree" = one value per line. "compact" = several values per line. "raw" = SNBT-style text, wrapped. */
    public String style = "tree";

    /**
     * Human readable output: "Hive Position: X 10, Y 64, Z -3", Yes/No, "30s (600 ticks)", Title Case labels.
     * Set to false to see raw keys and values instead.
     */
    public boolean readable = true;

    /** Hold this key to show the expanded view, like F3+H does: shift, ctrl, alt or none. */
    public String expandKey = "shift";

    /** Only show the data section when advanced tooltips (F3+H) are on. */
    public boolean requireAdvancedTooltips = false;

    /** Show the small "Item data" heading line. */
    public boolean showHeader = true;

    /** Add a "Contents: 14 stacks, 612 items" summary for shulker boxes, bundles and other containers. */
    public boolean containerSummary = true;

    /** Append a "Data: 6 components, 1.2 KB" line. */
    public boolean showStats = false;

    /** Show the raw component id next to each name, e.g. "Custom Data (minecraft:custom_data)". */
    public boolean showComponentIds = false;

    /** Sort keys alphabetically (NBT has no stable order, so this makes the data easy to scan). */
    public boolean sortKeys = true;

    /** 1234567 -> 1,234,567. */
    public boolean groupNumbers = true;

    // ------------------------------------------------------------------ SHOW ALL mode
    /**
     * Show ALL data: nothing is filtered, every list is complete, vanilla-shown components, names, lore, books,
     * full container contents, item id and count are included. Always on when true; otherwise hold allKey.
     */
    public boolean showAll = false;

    /** Hold this key to show ALL data for the item under the mouse: ctrl, shift, alt or none. */
    public String allKey = "ctrl";

    /** Line cap in ALL mode. 0 = fit the screen automatically, -1 = unlimited, otherwise that many lines. */
    public int allMaxLines = 0;

    /** In ALL mode, switch to the compact layout automatically when the one-per-line layout would not fit. */
    public boolean allAutoCompact = true;

    // ------------------------------------------------------------------ size limits
    /** Max data lines in a normal tooltip. */
    public int maxLines = 12;
    /** Max data lines in the expanded view (F3+H or the expand key). */
    public int maxLinesAdvanced = 40;
    /** Soft width limit of one line, in characters. Longer values are cut with an ellipsis. */
    public int maxLineLength = 76;
    /** How many elements of a long list are shown (normal / expanded). */
    public int maxListEntries = 3;
    public int maxListEntriesAdvanced = 8;

    // ------------------------------------------------------------------ colors (Minecraft color names)
    public String titleColor = "gold";
    public String labelColor = "yellow";
    public String valueColor = "gray";
    public String coordColor = "green";
    public String highlightColor = "light_purple";
    public String mutedColor = "dark_gray";

    // ------------------------------------------------------------------ filters
    /** Keys to color with highlightColor, e.g. ["hive_pos", "owner"]. Matches the readable or the raw name. */
    public List<String> highlightKeys = new ArrayList<>();

    /** Items that never get a data section. Exact ids or globs: "minecraft:written_book", "*_spawn_egg", "mymod:*". */
    public List<String> ignoredItems = new ArrayList<>();

    /** If not empty, ONLY these items get a data section (same id/glob syntax as ignoredItems). */
    public List<String> onlyItems = new ArrayList<>();

    /** If not empty, ONLY these components are shown, e.g. ["custom_data", "bees"]. (Ignored in ALL mode.) */
    public List<String> onlyComponents = new ArrayList<>();

    /** Components that are never shown. (Ignored in ALL mode.) */
    public List<String> ignoredComponents = new ArrayList<>(List.of(
            "tooltip_display", "tooltip_style", "creative_slot_lock", "map_post_processing",
            "damage", "max_damage", "max_stack_size", "custom_name", "item_name", "lore",
            "container", "bundle_contents", "written_book_content", "writable_book_content", "rarity"));

    /** Components vanilla already prints; we only add them if the item hides them via tooltip_display. */
    public List<String> vanillaShownComponents = new ArrayList<>(List.of(
            "enchantments", "stored_enchantments", "attribute_modifiers", "potion_contents", "trim",
            "jukebox_playable", "unbreakable", "dyed_color", "fireworks", "firework_explosion",
            "banner_patterns", "suspicious_stew_effects", "charged_projectiles",
            "ominous_bottle_amplifier", "instrument"));

    /** Vanilla-shown components that we ALSO print in raw form (exact levels, ids) in the expanded view. */
    public List<String> advancedAlsoShows = new ArrayList<>(List.of(
            "attribute_modifiers", "enchantments", "stored_enchantments", "potion_contents"));

    /** Components only shown in the expanded view (they are bulky). */
    public List<String> advancedOnlyComponents = new ArrayList<>(List.of(
            "item_model", "tool", "equippable", "weapon", "blocks_attacks"));

    /** NBT keys inside entity/block data that are pure noise. (Ignored in ALL mode.) */
    public List<String> ignoredKeys = new ArrayList<>(List.of(
            "Motion", "Rotation", "FallDistance", "Air", "OnGround", "Invulnerable", "PortalCooldown",
            "Fire", "UUID", "Brain", "CanPickUpLoot", "LeftHanded", "DeathTime", "HurtTime",
            "HurtByTimestamp", "AbsorptionAmount", "Pos"));

    // ------------------------------------------------------------------ derived (not saved)
    private transient Set<String> ignoredSet, onlySet, vanillaSet, advAlsoSet, advOnlySet, keySet, highlightSet;
    private transient List<Pattern> ignoredItemPatterns = List.of(), onlyItemPatterns = List.of();
    public transient ChatFormatting fmtTitle = ChatFormatting.GOLD, fmtLabel = ChatFormatting.YELLOW,
            fmtValue = ChatFormatting.GRAY, fmtCoord = ChatFormatting.GREEN,
            fmtHighlight = ChatFormatting.LIGHT_PURPLE, fmtMuted = ChatFormatting.DARK_GRAY;

    public boolean ignored(String name) { return ignoredSet.contains(name); }
    public boolean notWanted(String name) { return !onlySet.isEmpty() && !onlySet.contains(name); }
    public boolean vanillaShown(String name) { return vanillaSet.contains(name); }
    public boolean advancedAlso(String name) { return advAlsoSet.contains(name); }
    public boolean advancedOnly(String name) { return advOnlySet.contains(name); }
    public boolean ignoredKey(String key) { return keySet.contains(key); }
    public boolean compact() { return "compact".equals(style); }
    public boolean raw() { return "raw".equals(style); }
    public boolean hasItemFilters() { return !ignoredItemPatterns.isEmpty() || !onlyItemPatterns.isEmpty(); }

    /** True if a label (readable or raw) is one of highlightKeys. */
    public boolean highlighted(String label) {
        return !highlightSet.isEmpty() && highlightSet.contains(Fmt.norm(label));
    }

    /** False if the item is filtered out by ignoredItems / onlyItems. */
    public boolean itemAllowed(String itemId) {
        String id = itemId.contains(":") ? itemId : "minecraft:" + itemId;
        for (Pattern p : ignoredItemPatterns) if (p.matcher(id).matches()) return false;
        if (!onlyItemPatterns.isEmpty()) {
            for (Pattern p : onlyItemPatterns) if (p.matcher(id).matches()) return true;
            return false;
        }
        return true;
    }

    private static Set<String> norm(List<String> in) {
        Set<String> s = new HashSet<>();
        if (in != null) for (String x : in) if (x != null) s.add(x.startsWith("minecraft:") ? x.substring(10) : x);
        return s;
    }

    /** "gold", "Light Purple", "dark-red" -> the matching ChatFormatting constant (it is an enum). */
    private static ChatFormatting color(String name, ChatFormatting fallback) {
        if (name == null) return fallback;
        try {
            return ChatFormatting.valueOf(name.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_'));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static Pattern glob(String g) {
        String s = g.contains(":") ? g : "minecraft:" + g;
        StringBuilder re = new StringBuilder();
        for (String part : s.split("\\*", -1)) {
            if (re.length() > 0) re.append(".*");
            re.append(Pattern.quote(part));
        }
        return Pattern.compile(re.toString());
    }

    private static List<Pattern> globs(List<String> in) {
        List<Pattern> pats = new ArrayList<>();
        if (in != null) for (String g : in) if (g != null && !g.isBlank()) pats.add(glob(g.trim()));
        return pats;
    }

    private static String oneOf(String v, String fallback, String... allowed) {
        String k = v == null ? fallback : v.toLowerCase(Locale.ROOT);
        for (String a : allowed) if (a.equals(k)) return k;
        return fallback;
    }

    /** Re-reads the lists and clamps values. Call after changing any field. */
    public void compile() {
        maxLines = Math.max(2, maxLines);
        maxLinesAdvanced = Math.max(2, maxLinesAdvanced);
        maxLineLength = Math.max(30, maxLineLength);
        maxListEntries = Math.max(1, maxListEntries);
        maxListEntriesAdvanced = Math.max(1, maxListEntriesAdvanced);
        allMaxLines = Math.max(-1, allMaxLines);
        style = oneOf(style, "tree", "tree", "compact", "raw");
        expandKey = oneOf(expandKey, "shift", "shift", "ctrl", "alt", "none");
        allKey = oneOf(allKey, "ctrl", "shift", "ctrl", "alt", "none");
        if (preset == null) preset = "balanced";

        ignoredSet = norm(ignoredComponents);
        onlySet = norm(onlyComponents);
        vanillaSet = norm(vanillaShownComponents);
        advAlsoSet = norm(advancedAlsoShows);
        advOnlySet = norm(advancedOnlyComponents);
        keySet = new HashSet<>(ignoredKeys == null ? List.of() : ignoredKeys);

        highlightSet = new HashSet<>();
        if (highlightKeys != null) for (String h : highlightKeys) if (h != null && !h.isBlank()) highlightSet.add(Fmt.norm(h));

        ignoredItemPatterns = globs(ignoredItems);
        onlyItemPatterns = globs(onlyItems);

        fmtTitle = color(titleColor, ChatFormatting.GOLD);
        fmtLabel = color(labelColor, ChatFormatting.YELLOW);
        fmtValue = color(valueColor, ChatFormatting.GRAY);
        fmtCoord = color(coordColor, ChatFormatting.GREEN);
        fmtHighlight = color(highlightColor, ChatFormatting.LIGHT_PURPLE);
        fmtMuted = color(mutedColor, ChatFormatting.DARK_GRAY);
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("nbtpeek.json");
    }

    /** Factory defaults (used by the "Reset" button). */
    public static PeekConfig defaults() {
        PeekConfig c = new PeekConfig();
        c.compile();
        return c;
    }

    public void save() {
        try {
            Files.writeString(file(), GSON.toJson(this));
        } catch (IOException e) {
            LOG.warn("Could not save {}", file(), e);
        }
    }

    public static PeekConfig load() {
        Path file = file();
        PeekConfig cfg = null;
        if (Files.exists(file)) {
            try {
                cfg = GSON.fromJson(Files.readString(file), PeekConfig.class);
            } catch (Exception e) {
                LOG.warn("Could not read {}, using defaults (the file is left untouched)", file, e);
            }
        }
        if (cfg == null) cfg = new PeekConfig();
        cfg.compile();
        // Only create the file when there is none: never overwrite a file the user edited (even if it has a typo).
        if (!Files.exists(file)) cfg.save();
        return cfg;
    }
}
