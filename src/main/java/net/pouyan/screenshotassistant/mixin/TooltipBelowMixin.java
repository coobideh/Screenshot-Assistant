package net.pouyan.screenshotassistant.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.TooltipPositioner;
import net.minecraft.client.font.TextRenderer;
import net.pouyan.screenshotassistant.config.ConfigManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

/**
 * Optionally shifts ALL tooltip Y positions by +100 px so tooltips appear
 * BELOW the cursor instead of above it.
 *
 * ── Why this affects all tooltips ───────────────────────────────────────────
 * Minecraft's tooltip rendering is centralised in DrawContext.drawTooltip().
 * There is no way to target only one specific screen without also touching
 * other screens, because the call chain does not carry screen identity.
 * This is documented transparently in the mod description and is user-toggleable
 * via "Fix tooltip position" in settings (default: ON).
 *
 * ── Safety ──────────────────────────────────────────────────────────────────
 * screenshotassistant.mixins.json sets "defaultRequire": 0, so if this
 * injection ever fails to match (e.g. after a Yarn rename), the game continues
 * to run normally – tooltips just revert to their default position.
 */
@Mixin(DrawContext.class)
public abstract class TooltipBelowMixin {

    @ModifyVariable(
        method = "drawTooltip(Lnet/minecraft/client/font/TextRenderer;Ljava/util/List;IILnet/minecraft/client/gui/tooltip/TooltipPositioner;)V",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 1   // second int parameter = y
    )
    private int forceTooltipBelow(int y) {
        if (!ConfigManager.get().fixTooltipPosition) return y;
        return y + 100;
    }
}
