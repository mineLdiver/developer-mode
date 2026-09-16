package net.mine_diver.developermode.client.gui.radial;

import net.mine_diver.developermode.client.DeveloperModeClient;
import net.mine_diver.developermode.client.gui.Draw;
import net.mine_diver.developermode.client.gui.EntityPreview;
import net.mine_diver.developermode.client.gui.Theme;
import net.mine_diver.developermode.client.inspect.InspectMode;
import net.mine_diver.developermode.feature.entity.Entities;
import net.mine_diver.developermode.feature.entity.EntityTargeting;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;

/**
 * The slot that drops you into {@link InspectMode}.
 *
 * <p>It does not open the editor itself. Picking an entity from behind the
 * composer's blur is disconnected from whatever is actually in front of you, so
 * this hands control back to the world instead, with entities outlined and a
 * click to take one.
 *
 * <p>The icon still previews whatever the crosshair was on when the menu key
 * went down, so the handover is continuous: you see the thing you are about to
 * go back out and click.
 */
public final class EntityEntry extends RadialEntry {
    private static final int ICON_WIDTH = 22;
    private static final int ICON_HEIGHT = 26;

    public EntityEntry() {
        super("Inspect", "Highlight entities and click one", null, returnTo -> {
            InspectMode.enter();
            DeveloperModeClient.minecraft().setScreen(null);
        });
    }

    @Override
    public String label() {
        return "Inspect";
    }

    @Override
    public String hint() {
        Entity target = EntityTargeting.current();
        return target == null ? "Highlight entities and click one" : Entities.name(target) + " is in your sights";
    }

    @Override
    public void renderIcon(Minecraft minecraft, int centerX, int centerY) {
        Entity target = EntityTargeting.current();
        if (target == null) {
            Draw.outline(centerX - 7, centerY - 7, 14, 14, Theme.TEXT_FAINT);
            return;
        }
        EntityPreview.render(minecraft, target,
                centerX - ICON_WIDTH / 2, centerY - ICON_HEIGHT / 2, ICON_WIDTH, ICON_HEIGHT, 0);
    }
}
