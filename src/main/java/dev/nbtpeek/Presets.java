package dev.nbtpeek;

import java.util.List;

/** One-click bundles of settings. They only touch layout/limit/content options, never colors or key choices. */
public final class Presets {
    public static final List<String> NAMES = List.of("minimal", "balanced", "detailed", "everything");

    private Presets() {}

    public static void apply(PeekConfig c, String name) {
        // start from the balanced defaults, then override
        c.style = "tree";
        c.maxLines = 12;
        c.maxLinesAdvanced = 40;
        c.maxListEntries = 3;
        c.maxListEntriesAdvanced = 8;
        c.containerSummary = true;
        c.showHeader = true;
        c.showStats = false;
        c.showAll = false;
        c.showComponentIds = false;
        c.allMaxLines = 0;
        c.allAutoCompact = true;

        switch (name) {
            case "minimal" -> {
                c.style = "compact";
                c.maxLines = 6;
                c.maxLinesAdvanced = 16;
                c.maxListEntries = 2;
                c.maxListEntriesAdvanced = 5;
                c.containerSummary = false;
            }
            case "detailed" -> {
                c.maxLines = 20;
                c.maxLinesAdvanced = 60;
                c.maxListEntries = 5;
                c.maxListEntriesAdvanced = 15;
                c.showStats = true;
            }
            case "everything" -> {
                c.showAll = true;
                c.showStats = true;
                c.showComponentIds = true;
            }
            default -> name = "balanced";
        }
        c.preset = name;
        c.compile();
    }

    public static String next(String current) {
        int i = NAMES.indexOf(current);
        return NAMES.get((i + 1) % NAMES.size());
    }
}
