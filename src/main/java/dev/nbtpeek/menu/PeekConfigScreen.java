package dev.nbtpeek.menu;

import dev.nbtpeek.NbtPeekClient;
import dev.nbtpeek.PeekConfig;
import dev.nbtpeek.Presets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Settings screen built from plain vanilla buttons (click a button to cycle its value), split into pages.
 * Changes apply instantly; they are written to config/nbtpeek.json when the screen is closed.
 */
public class PeekConfigScreen extends Screen {
    private static final String[] COLORS = {
            "gold", "yellow", "white", "gray", "dark_gray", "green", "dark_green", "aqua", "dark_aqua",
            "blue", "light_purple", "dark_purple", "red", "dark_red"};

    private record Opt(String name, Supplier<String> value, Runnable next) {}

    private record Page(String title, List<Opt> options) {}

    private final Screen parent;
    private final int page;

    public PeekConfigScreen(Screen parent) {
        this(parent, 0);
    }

    public PeekConfigScreen(Screen parent, int page) {
        super(Component.literal("NBT Peek Settings"));
        this.parent = parent;
        this.page = page;
    }

    private static PeekConfig cfg() {
        return NbtPeekClient.CONFIG;
    }

    // ---------------------------------------------------------------- options

    static List<Page> pages() {
        List<Page> p = new ArrayList<>();

        List<Opt> general = new ArrayList<>();
        general.add(new Opt("Preset", () -> cap(cfg().preset), () -> Presets.apply(cfg(), Presets.next(cfg().preset))));
        general.add(new Opt("Enabled", () -> onOff(cfg().enabled), () -> cfg().enabled = !cfg().enabled));
        general.add(new Opt("Show ALL data", () -> onOff(cfg().showAll), () -> cfg().showAll = !cfg().showAll));
        general.add(new Opt("ALL-data key", () -> keyName(cfg().allKey), () -> cfg().allKey = next(cfg().allKey, "ctrl", "alt", "shift", "none")));
        general.add(new Opt("Layout", () -> switch (cfg().style) {
            case "compact" -> "Compact";
            case "raw" -> "Raw SNBT";
            default -> "One per line";
        }, () -> cfg().style = next(cfg().style, "tree", "compact", "raw")));
        general.add(new Opt("Expand key", () -> keyName(cfg().expandKey), () -> cfg().expandKey = next(cfg().expandKey, "shift", "ctrl", "alt", "none")));
        general.add(new Opt("Readable names", () -> onOff(cfg().readable), () -> cfg().readable = !cfg().readable));
        general.add(new Opt("Only with F3+H", () -> onOff(cfg().requireAdvancedTooltips),
                () -> cfg().requireAdvancedTooltips = !cfg().requireAdvancedTooltips));
        general.add(new Opt("Show heading", () -> onOff(cfg().showHeader), () -> cfg().showHeader = !cfg().showHeader));
        p.add(new Page("General", general));

        List<Opt> limits = new ArrayList<>();
        limits.add(new Opt("Max lines", () -> String.valueOf(cfg().maxLines),
                () -> cfg().maxLines = next(cfg().maxLines, 6, 8, 10, 12, 16, 20, 30)));
        limits.add(new Opt("Max lines (expanded)", () -> String.valueOf(cfg().maxLinesAdvanced),
                () -> cfg().maxLinesAdvanced = next(cfg().maxLinesAdvanced, 20, 30, 40, 60, 80)));
        limits.add(new Opt("Max lines (ALL)", () -> cfg().allMaxLines == 0 ? "Fit screen" : cfg().allMaxLines < 0 ? "Unlimited" : String.valueOf(cfg().allMaxLines),
                () -> cfg().allMaxLines = next(cfg().allMaxLines, 0, 25, 50, 100, 200, -1)));
        limits.add(new Opt("ALL: auto-compact", () -> onOff(cfg().allAutoCompact), () -> cfg().allAutoCompact = !cfg().allAutoCompact));
        limits.add(new Opt("Line width", () -> String.valueOf(cfg().maxLineLength),
                () -> cfg().maxLineLength = next(cfg().maxLineLength, 50, 60, 76, 90, 110)));
        limits.add(new Opt("List entries", () -> String.valueOf(cfg().maxListEntries), () -> {
            cfg().maxListEntries = next(cfg().maxListEntries, 2, 3, 4, 6);
            cfg().maxListEntriesAdvanced = cfg().maxListEntries * 3;
        }));
        p.add(new Page("Limits", limits));

        List<Opt> content = new ArrayList<>();
        content.add(new Opt("Container summary", () -> onOff(cfg().containerSummary), () -> cfg().containerSummary = !cfg().containerSummary));
        content.add(new Opt("Show stats line", () -> onOff(cfg().showStats), () -> cfg().showStats = !cfg().showStats));
        content.add(new Opt("Show component ids", () -> onOff(cfg().showComponentIds), () -> cfg().showComponentIds = !cfg().showComponentIds));
        content.add(new Opt("Sort keys A-Z", () -> onOff(cfg().sortKeys), () -> cfg().sortKeys = !cfg().sortKeys));
        content.add(new Opt("Group big numbers", () -> onOff(cfg().groupNumbers), () -> cfg().groupNumbers = !cfg().groupNumbers));
        p.add(new Page("Content", content));

        List<Opt> colors = new ArrayList<>();
        colors.add(new Opt("Title color", () -> cfg().titleColor, () -> cfg().titleColor = next(cfg().titleColor, COLORS)));
        colors.add(new Opt("Label color", () -> cfg().labelColor, () -> cfg().labelColor = next(cfg().labelColor, COLORS)));
        colors.add(new Opt("Value color", () -> cfg().valueColor, () -> cfg().valueColor = next(cfg().valueColor, COLORS)));
        colors.add(new Opt("Coordinate color", () -> cfg().coordColor, () -> cfg().coordColor = next(cfg().coordColor, COLORS)));
        colors.add(new Opt("Highlight color", () -> cfg().highlightColor, () -> cfg().highlightColor = next(cfg().highlightColor, COLORS)));
        colors.add(new Opt("Muted color", () -> cfg().mutedColor, () -> cfg().mutedColor = next(cfg().mutedColor, COLORS)));
        p.add(new Page("Colors", colors));
        return p;
    }

    private static String onOff(boolean b) {
        return b ? "ON" : "OFF";
    }

    private static String cap(String s) {
        return s == null || s.isEmpty() ? "" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String keyName(String k) {
        return k.equals("none") ? "None" : cap(k);
    }

    private static int next(int cur, int... values) {
        for (int i = 0; i < values.length; i++) if (values[i] == cur) return values[(i + 1) % values.length];
        return values[0];
    }

    private static String next(String cur, String... values) {
        for (int i = 0; i < values.length; i++) if (values[i].equalsIgnoreCase(cur)) return values[(i + 1) % values.length];
        return values[0];
    }

    // ---------------------------------------------------------------- layout

    @Override
    protected void init() {
        List<Page> pages = pages();
        int pageIndex = Math.max(0, Math.min(page, pages.size() - 1));
        Page current = pages.get(pageIndex);
        List<Opt> opts = current.options();

        int gap = 6;
        int colW = Math.min(170, (this.width - 20 - gap) / 2);
        int left = (this.width - (colW * 2 + gap)) / 2;
        int rows = Math.max(1, (opts.size() + 1) / 2);
        int top = 32;
        int rowH = Math.max(21, Math.min(24, (this.height - top - 56) / rows));

        Button title = Button.builder(this.title, b -> { }).bounds(left, 8, colW * 2 + gap, 20).build();
        title.active = false;
        this.addRenderableWidget(title);

        for (int i = 0; i < opts.size(); i++) {
            Opt opt = opts.get(i);
            int x = left + (i % 2) * (colW + gap);
            int y = top + (i / 2) * rowH;
            this.addRenderableWidget(Button.builder(label(opt), btn -> {
                opt.next().run();
                cfg().compile();
                btn.setMessage(label(opt));
            }).bounds(x, y, colW, 20).build());
        }

        // page navigation
        int ny = this.height - 50;
        int navW = Math.min(60, colW / 2);
        int midW = colW * 2 + gap - navW * 2 - gap * 2;
        int last = pages.size() - 1;
        Button prev = Button.builder(Component.literal("<"),
                b -> Minecraft.getInstance().gui.setScreen(new PeekConfigScreen(parent, pageIndex == 0 ? last : pageIndex - 1)))
                .bounds(left, ny, navW, 20).build();
        Button label = Button.builder(Component.literal("Page " + (pageIndex + 1) + "/" + pages.size() + ": " + current.title()), b -> { })
                .bounds(left + navW + gap, ny, midW, 20).build();
        label.active = false;
        Button nextBtn = Button.builder(Component.literal(">"),
                b -> Minecraft.getInstance().gui.setScreen(new PeekConfigScreen(parent, pageIndex == last ? 0 : pageIndex + 1)))
                .bounds(left + navW + gap + midW + gap, ny, navW, 20).build();
        this.addRenderableWidget(prev);
        this.addRenderableWidget(label);
        this.addRenderableWidget(nextBtn);

        int by = this.height - 26;
        this.addRenderableWidget(Button.builder(Component.literal("Reset to defaults"), btn -> {
            NbtPeekClient.CONFIG = PeekConfig.defaults();
            Minecraft.getInstance().gui.setScreen(new PeekConfigScreen(parent, pageIndex));
        }).bounds(left, by, colW, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Done"), btn -> this.onClose())
                .bounds(left + colW + gap, by, colW, 20).build());
    }

    private static Component label(Opt opt) {
        return Component.literal(opt.name() + ": " + opt.value().get());
    }

    @Override
    public void onClose() {
        cfg().compile();
        cfg().save();
        Minecraft.getInstance().gui.setScreen(parent);
    }
}
