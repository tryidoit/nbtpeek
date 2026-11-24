package dev.nbtpeek;

import net.minecraft.world.item.ItemStack;

/**
 * Expands the tooltip once the cursor has rested on the same item for a moment.
 * Tooltips are rebuilt every frame while hovering, so "still hovering" simply means "asked again recently".
 */
final class HoverExpand {
    /** If no tooltip was built for this long, the cursor has left the item and the timer restarts. */
    private static final long GAP_MS = 250;

    private static ItemStack last;
    private static long since;
    private static long lastSeen;

    private HoverExpand() {}

    /** True once the same item has been hovered for at least delayMs. Call once per tooltip build. */
    static boolean dwelled(ItemStack stack, int delayMs) {
        long now = System.nanoTime() / 1_000_000L;
        if (last == null || now - lastSeen > GAP_MS || !same(last, stack)) {
            last = stack;
            since = now;
        }
        lastSeen = now;
        return now - since >= delayMs;
    }

    private static boolean same(ItemStack a, ItemStack b) {
        if (a == b) return true;
        return a.getItem() == b.getItem()
                && a.getCount() == b.getCount()
                && a.getComponents().equals(b.getComponents());
    }
}
