package dev.nbtpeek;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NbtPeekClient implements ClientModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("nbtpeek");
    public static PeekConfig CONFIG;
    private static boolean warned;

    @Override
    public void onInitializeClient() {
        CONFIG = PeekConfig.load();
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
            // An empty list means the item hides its tooltip entirely: respect that.
            if (!CONFIG.enabled || lines.isEmpty()) return;
            try {
                TooltipPeek.append(stack, context, flag, lines);
            } catch (Exception e) {
                if (!warned) { // log once, never spam or crash the tooltip
                    warned = true;
                    LOG.warn("NBT Peek failed on a tooltip", e);
                }
            }
        });
    }
}
