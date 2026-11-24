package dev.nbtpeek;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Builds the "Item data" tooltip section from an ItemStack's data components. */
final class TooltipPeek {
    /** Shown first, so they survive the line cap. */
    private static final List<String> PRIORITY = List.of(
            "custom_data", "block_entity_data", "entity_data", "bucket_entity_data", "bees",
            "lodestone_tracker", "attribute_modifiers", "container", "bundle_contents", "custom_model_data");

    /** One rendered component. json == null for the synthetic "Item" / "Data" blocks. */
    private record Block(int priority, String name, JsonElement json, List<JsonRenderer.Entry> entries) {}

    private TooltipPeek() {}

    static void append(ItemStack stack, Item.TooltipContext ctx, TooltipFlag flag, List<Component> lines) {
        PeekConfig cfg = NbtPeekClient.CONFIG;
        boolean advancedTooltips = flag.isAdvanced();
        boolean all = cfg.showAll || ExpandKey.isDown(cfg.allKey);
        if (cfg.requireAdvancedTooltips && !advancedTooltips && !all) return;

        String itemId = null;
        if (cfg.hasItemFilters() || all || cfg.showStats) {
            itemId = String.valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem()));
            if (!cfg.itemAllowed(itemId)) return;
        }

        HolderLookup.Provider registries = ctx.registries();
        if (registries == null) {
            var level = Minecraft.getInstance().level;
            if (level == null) return;
            registries = level.registryAccess();
        }
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registries);

        // "Expanded" = F3+H, a rested cursor, or the optional expand key. ALL data implies expanded.
        boolean dwelled = cfg.expandHoverMs >= 0 && HoverExpand.dwelled(stack, cfg.expandHoverMs);
        boolean expanded = all || advancedTooltips || dwelled || ExpandKey.isDown(cfg.expandKey);
        int width = all ? Math.max(cfg.maxLineLength, ScreenInfo.charsAcross()) : cfg.maxLineLength;
        View view = View.of(cfg, expanded, all, width);

        // Pass 1: which components does this item hide from the vanilla tooltip? (irrelevant in ALL mode)
        Set<String> hidden = new HashSet<>();
        if (!all) {
            for (TypedDataComponent<?> c : stack.getComponents()) {
                if (!idOf(c).equals("minecraft:tooltip_display")) continue;
                JsonElement j = encode(c, ops);
                if (j != null && j.isJsonObject() && j.getAsJsonObject().get("hidden_components") instanceof JsonArray arr) {
                    for (JsonElement x : arr) {
                        if (!x.isJsonPrimitive()) continue;
                        String s = x.getAsString();
                        hidden.add(s.contains(":") ? s : "minecraft:" + s);
                    }
                }
            }
        }

        // Pass 2: render everything that should be shown.
        List<Block> items = new ArrayList<>();
        int components = 0;
        long chars = 0;
        for (TypedDataComponent<?> c : stack.getComponents()) {
            String id = idOf(c);
            String name = Fmt.strip(id);
            components++;
            if (!all) {
                boolean containerLike = name.equals("container") || name.equals("bundle_contents");
                if (cfg.notWanted(name)) continue;
                if (cfg.ignored(name) && !(containerLike && cfg.containerSummary)) continue;
                if (cfg.vanillaShown(name) && !hidden.contains(id) && !(expanded && cfg.advancedAlso(name))) continue;
                if (cfg.advancedOnly(name) && !expanded) continue;
            }

            JsonElement json = encode(c, ops);
            List<JsonRenderer.Entry> entries = new ArrayList<>();
            if (json != null) {
                chars += json.toString().length();
                new JsonRenderer(cfg, view, entries).root(id, json);
            } else if (all) { // not serializable: still show what we can
                String label = cfg.readable ? Fmt.pretty(name) : name;
                entries.add(new JsonRenderer.Entry(0, label, clip(String.valueOf(c.value()), width) + "  (not serializable)", false, false));
            }
            if (entries.isEmpty()) continue;

            int p = PRIORITY.indexOf(name);
            items.add(new Block(p >= 0 ? p : 100, name, json, entries));
        }

        // Synthetic blocks: what the stack is, and how much data it carries.
        if (all) {
            String idPath = itemId == null ? "?" : itemId;
            String pretty = cfg.readable ? Fmt.pretty(idPath) : idPath;
            items.add(new Block(-1, "item", null, List.of(new JsonRenderer.Entry(0, "Item",
                    pretty + " (" + idPath + ") \u00D7" + stack.getCount(), false, false))));
        }
        if (all || cfg.showStats) {
            items.add(new Block(200, "data", null, List.of(new JsonRenderer.Entry(0, "Data",
                    components + (components == 1 ? " component, " : " components, ") + Fmt.bytes(chars) + " as JSON",
                    false, false))));
        }
        if (items.isEmpty()) return;
        items.sort(Comparator.comparingInt(Block::priority)); // stable: keeps registry order within a tier

        // Layout + line budget.
        String style = cfg.style;
        List<Component> body = layout(items, style, cfg, width);
        int max;
        if (all) {
            max = cfg.allMaxLines < 0 ? Integer.MAX_VALUE
                    : cfg.allMaxLines > 0 ? cfg.allMaxLines : ScreenInfo.capacity(lines.size() + (cfg.showHeader ? 1 : 0));
            if (cfg.allAutoCompact && body.size() > max && !style.equals("compact")) {
                List<Component> packed = layout(items, "compact", cfg, width);
                if (packed.size() < body.size()) body = packed;
            }
        } else {
            max = expanded ? cfg.maxLinesAdvanced : cfg.maxLines;
        }
        int shown = body.size() > max ? Math.max(1, max - 1) : body.size();

        if (cfg.showHeader) {
            lines.add(Component.literal(all ? "Item data (all)" : "Item data").withStyle(cfg.fmtMuted, ChatFormatting.ITALIC));
        }
        for (int i = 0; i < shown; i++) lines.add(body.get(i));
        if (body.size() > shown) {
            String hint = all ? " (raise \"Max lines (ALL)\" in the settings)" : expanded ? "" : hint(cfg);
            lines.add(Component.literal("\u2026 +" + (body.size() - shown) + " more lines" + hint).withStyle(cfg.fmtMuted));
        }
    }

    private static String hint(PeekConfig cfg) {
        boolean keys = ExpandKey.available();
        List<String> ways = new ArrayList<>();
        if (cfg.expandHoverMs >= 0) ways.add("keep hovering");
        if (keys && !cfg.expandKey.equals("none")) ways.add("hold " + ExpandKey.displayName(cfg.expandKey));
        ways.add("F3+H");
        String all = keys && !cfg.allKey.equals("none") ? ", hold " + ExpandKey.displayName(cfg.allKey) + " for ALL" : "";
        return " (" + String.join(" or ", ways) + " for more" + all + ")";
    }

    // ---------------------------------------------------------------- layouts

    private static List<Component> layout(List<Block> items, String style, PeekConfig cfg, int width) {
        List<Component> out = new ArrayList<>();
        for (Block it : items) {
            switch (style) {
                case "compact" -> out.addAll(compact(it.entries(), cfg, width));
                case "raw" -> out.addAll(it.json() == null ? tree(it.entries(), cfg, width) : raw(it, cfg, width));
                default -> out.addAll(tree(it.entries(), cfg, width));
            }
        }
        return out;
    }

    /** One entry per line. */
    private static List<Component> tree(List<JsonRenderer.Entry> es, PeekConfig cfg, int width) {
        List<Component> out = new ArrayList<>(es.size());
        for (JsonRenderer.Entry e : es) out.add(toComponent(e, cfg, width));
        return out;
    }

    /** SNBT-style text, wrapped at the line width. */
    private static List<Component> raw(Block it, PeekConfig cfg, int width) {
        JsonElement data = cfg.showAll ? it.json() : JsonRenderer.pruneDeep(cfg, it.json());
        String text = Fmt.snbt(data);
        String head = it.name();
        List<Component> out = new ArrayList<>();
        int first = Math.max(15, width - head.length() - 2);
        int rest = Math.max(20, width - 1);
        int pos = 0;
        boolean firstLine = true;
        while (pos < text.length() || firstLine) {
            int n = firstLine ? first : rest;
            String chunk = text.substring(pos, Math.min(text.length(), pos + n));
            pos += chunk.length();
            if (firstLine) {
                out.add(Component.literal(head + ": ").withStyle(cfg.fmtTitle)
                        .append(Component.literal(chunk).withStyle(cfg.fmtValue)));
                firstLine = false;
            } else {
                out.add(Component.literal(" ").append(Component.literal(chunk).withStyle(cfg.fmtValue)));
            }
            if (chunk.isEmpty()) break;
        }
        return out;
    }

    /** Several "Label: value" pairs per line, wrapped at the line width. */
    private static List<Component> compact(List<JsonRenderer.Entry> es, PeekConfig cfg, int width) {
        List<Component> out = new ArrayList<>();
        if (es.size() == 1) {
            out.add(toComponent(es.get(0), cfg, width));
            return out;
        }
        out.add(toComponent(es.get(0), cfg, width)); // the component's own title line

        String[] parents = new String[32];
        MutableComponent line = null;
        int len = 0;
        int max = width - 1;
        for (int i = 1; i < es.size(); i++) {
            JsonRenderer.Entry e = es.get(i);
            int d = Math.min(e.depth(), parents.length - 1);
            for (int k = d; k < parents.length; k++) parents[k] = null;
            if (e.value() == null) {          // header: remember it as a path prefix for its children
                parents[d] = e.label();
                continue;
            }
            StringBuilder lab = new StringBuilder();
            for (int k = 1; k < d; k++) if (parents[k] != null) lab.append(parents[k]).append(JsonRenderer.SEP);
            lab.append(e.label());
            String label = lab.toString();
            String value = e.value();
            int room = Math.max(10, max - label.length() - 4);
            if (value.length() > room) value = value.substring(0, room - 1) + "\u2026";

            int tokenLen = label.length() + 2 + value.length();
            if (line != null && len + 2 + tokenLen > max) {
                out.add(line);
                line = null;
            }
            if (line == null) {
                line = Component.literal(" ");
                len = 1;
            } else {
                line.append(Component.literal("; ").withStyle(cfg.fmtMuted));
                len += 2;
            }
            line.append(Component.literal(label + ": ").withStyle(e.highlight() ? cfg.fmtHighlight : cfg.fmtLabel));
            line.append(Component.literal(value).withStyle(valueStyle(e, cfg)));
            len += tokenLen;
        }
        if (line != null) out.add(line);
        return out;
    }

    private static ChatFormatting valueStyle(JsonRenderer.Entry e, PeekConfig cfg) {
        return e.highlight() ? cfg.fmtHighlight : e.coord() ? cfg.fmtCoord : cfg.fmtValue;
    }

    private static MutableComponent toComponent(JsonRenderer.Entry e, PeekConfig cfg, int width) {
        String pad = " ".repeat(Math.min(e.depth(), 4));
        String label = e.label();
        int labelMax = Math.max(20, width / 2);
        if (label.length() > labelMax) label = label.substring(0, labelMax - 1) + "\u2026";

        ChatFormatting labelStyle = e.highlight() ? cfg.fmtHighlight : e.depth() == 0 ? cfg.fmtTitle : cfg.fmtLabel;
        MutableComponent c = Component.literal(pad + label + (e.value() == null ? ":" : ": ")).withStyle(labelStyle);
        if (e.value() != null) {
            String v = e.value();
            int room = Math.max(12, width - pad.length() - label.length() - 2);
            if (v.length() > room) v = v.substring(0, room - 1) + "\u2026";
            c.append(Component.literal(v).withStyle(valueStyle(e, cfg)));
        }
        return c;
    }

    private static String clip(String s, int max) {
        return s.length() > max ? s.substring(0, max - 1) + "\u2026" : s;
    }

    // ---------------------------------------------------------------- encoding

    private static String idOf(TypedDataComponent<?> c) {
        return String.valueOf(BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(c.type()));
    }

    private static JsonElement encode(TypedDataComponent<?> c, RegistryOps<JsonElement> ops) {
        try {
            return c.encodeValue(ops).result().orElse(null);
        } catch (RuntimeException ex) { // transient / non-serializable components
            return null;
        }
    }
}
