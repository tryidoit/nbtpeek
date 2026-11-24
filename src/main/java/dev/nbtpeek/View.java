package dev.nbtpeek;

/** How much to show for one tooltip. expanded = F3+H or expand key; all = show-everything mode. */
record View(boolean expanded, boolean all, int width, int listLimit, int maxDepth) {
    static View of(PeekConfig cfg, boolean expanded, boolean all, int width) {
        int list = all ? Integer.MAX_VALUE : expanded ? cfg.maxListEntriesAdvanced : cfg.maxListEntries;
        return new View(expanded || all, all, width, list, all ? 14 : 5);
    }
}
