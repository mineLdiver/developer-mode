package net.mine_diver.developermode.client.gui.radial;

import net.mine_diver.developermode.client.DeveloperModeClient;
import net.mine_diver.developermode.client.gui.DevScreen;
import net.mine_diver.developermode.client.gui.Draw;
import net.mine_diver.developermode.client.gui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * The hold to open ring.
 *
 * <p>It always sits in the middle of the screen. There is no pointer: the mouse
 * is grabbed while the ring is up and its movement is read as raw deflection,
 * like pushing a stick.
 *
 * <p>Two things respond, and they respond differently on purpose. The glow in
 * the hole tracks the stick continuously, so pushing into empty space still
 * feels connected. The ring itself ignores that and only steps, by a fixed
 * amount, once the push actually lands on a slot, so it reads as a commitment
 * rather than as drifting furniture.
 */
public final class RadialScreen extends DevScreen {
    private static final float INNER_RADIUS = 26;
    private static final float OUTER_RADIUS = 74;
    private static final float SLOT_GAP_DEGREES = 3;

    /** GUI pixels of mouse travel for a fully pushed stick. */
    private static final float FULL_PULL = 40;
    /** Travel needed before the push lands on a slot at all. */
    private static final float DEAD_ZONE = 11;
    /** Mouse travel to stick travel. Raise it for a twitchier ring. */
    private static final float SENSITIVITY = 1;

    /** How far the glow travels for a full push. Keep it inside the hole. */
    private static final float GLOW_TRAVEL = 0.45F;
    private static final float GLOW_RADIUS = 17;

    /** Fixed step the ring takes towards a chosen slot. */
    private static final float SNAP_DISTANCE = 6;
    /** Time constant of that step. Smaller is snappier. */
    private static final float SNAP_TAU_MILLIS = 45;

    private static final long OPEN_MILLIS = 110;

    private final Screen returnTo;

    private float pullX;
    private float pullY;
    private float snapX;
    private float snapY;
    private int hovered = -1;
    private boolean committed;
    private long openedAt;
    private long lastFrameAt;

    /** Where the pointer was before the grab, in window pixels. */
    private int restoreCursorX;
    private int restoreCursorY;

    private RadialScreen(Screen returnTo) {
        this.returnTo = returnTo;
    }

    public static void open(Screen returnTo) {
        Minecraft.INSTANCE.setScreen(new RadialScreen(returnTo));
    }

    @Override
    public void init() {
        restoreCursorX = Mouse.getX();
        restoreCursorY = Mouse.getY();

        Mouse.setGrabbed(true);
        drainMouseDeltas();

        pullX = 0;
        pullY = 0;
        snapX = 0;
        snapY = 0;
        openedAt = System.currentTimeMillis();
        lastFrameAt = openedAt;
    }

    @Override
    public void removed() {
        // Put the pointer back exactly where it was, so returning to the
        // composer does not teleport its cursor to the middle of the screen.
        Mouse.setCursorPosition(restoreCursorX, restoreCursorY);
        Mouse.setGrabbed(false);
        drainMouseDeltas();
    }

    @Override
    public void render(int mouseX, int mouseY, float delta) {
        long now = System.currentTimeMillis();
        float elapsed = now - lastFrameAt;
        lastFrameAt = now;

        updatePull();
        hovered = slotUnderPull();
        updateSnap(elapsed);

        // Polled here rather than off a key event so the ring closes on the
        // frame the key comes up instead of on the next twentieth of a second.
        if (!Keyboard.isKeyDown(DeveloperModeClient.MENU_KEY.code)) {
            commit();
            return;
        }

        float grow = openProgress();
        renderBackdrop();
        renderRing(grow);
        renderGlow();
        renderIcons(grow);
        renderCaption();
    }

    @Override
    protected void keyPressed(char character, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            hovered = -1;
            commit();
        }
    }

    private void commit() {
        if (committed) return;
        committed = true;

        RadialEntry entry = RadialMenu.get(hovered);
        if (entry == null || !entry.enabled()) {
            minecraft.setScreen(returnTo);
        } else {
            entry.perform(returnTo);
        }
    }

    private void updatePull() {
        float scale = Draw.scaleFactor(minecraft);
        pullX += Mouse.getDX() / scale * SENSITIVITY;
        pullY -= Mouse.getDY() / scale * SENSITIVITY;

        float distance = (float) Math.sqrt(pullX * pullX + pullY * pullY);
        if (distance > FULL_PULL) {
            pullX = pullX / distance * FULL_PULL;
            pullY = pullY / distance * FULL_PULL;
        }
    }

    /**
     * Eases the ring towards its step. Time based rather than per frame, so the
     * step lands in the same wall clock time whatever the frame rate.
     */
    private void updateSnap(float elapsedMillis) {
        double targetAngle = hovered < 0 ? 0 : Math.toRadians(hovered * 360.0 / RadialMenu.SLOTS);
        float targetX = hovered < 0 ? 0 : (float) Math.sin(targetAngle) * SNAP_DISTANCE;
        float targetY = hovered < 0 ? 0 : (float) -Math.cos(targetAngle) * SNAP_DISTANCE;

        float alpha = (float) (1 - Math.exp(-elapsedMillis / SNAP_TAU_MILLIS));
        snapX += (targetX - snapX) * alpha;
        snapY += (targetY - snapY) * alpha;
    }

    private int slotUnderPull() {
        if (pullX * pullX + pullY * pullY < DEAD_ZONE * DEAD_ZONE) return -1;

        double degrees = Math.toDegrees(Math.atan2(pullX, -pullY));
        if (degrees < 0) degrees += 360;

        double slice = 360.0 / RadialMenu.SLOTS;
        int slot = (int) Math.floor((degrees + slice / 2) / slice) % RadialMenu.SLOTS;
        return RadialMenu.get(slot) == null ? -1 : slot;
    }

    private void renderRing(float grow) {
        float ringX = ringX();
        float ringY = ringY();
        float inner = INNER_RADIUS * grow;
        float outer = OUTER_RADIUS * grow;

        // A faint disc behind the hole, so the caption stays readable over
        // whatever the world happens to be doing.
        Draw.ring(ringX, ringY, 0, inner, 0, 360, Theme.PANEL_SUNKEN);

        double slice = 360.0 / RadialMenu.SLOTS;
        for (int slot = 0; slot < RadialMenu.SLOTS; slot++) {
            double middle = slot * slice;
            double from = middle - slice / 2 + SLOT_GAP_DEGREES / 2;
            double to = middle + slice / 2 - SLOT_GAP_DEGREES / 2;

            RadialEntry entry = RadialMenu.get(slot);
            boolean filled = entry != null && entry.enabled();
            boolean selected = slot == hovered;
            int color = selected && filled ? Theme.ACCENT_FILL : entry != null ? Theme.PANEL : Theme.PANEL_SUNKEN;

            Draw.ring(ringX, ringY, inner, outer, from, to, color);
            if (selected && filled) {
                Draw.ring(ringX, ringY, outer - 2, outer, from, to, Theme.ACCENT);
            }
        }
    }

    private void renderGlow() {
        Draw.glow(
                width / 2F + pullX * GLOW_TRAVEL,
                height / 2F + pullY * GLOW_TRAVEL,
                GLOW_RADIUS, Theme.GLOW);
    }

    private void renderIcons(float grow) {
        float ringX = ringX();
        float ringY = ringY();
        float radius = (INNER_RADIUS + OUTER_RADIUS) / 2 * grow;
        double slice = 360.0 / RadialMenu.SLOTS;

        for (int slot = 0; slot < RadialMenu.SLOTS; slot++) {
            RadialEntry entry = RadialMenu.get(slot);
            if (entry == null) continue;

            double radians = Math.toRadians(slot * slice);
            int x = Math.round((float) (ringX + Math.sin(radians) * radius));
            int y = Math.round((float) (ringY - Math.cos(radians) * radius));
            entry.renderIcon(minecraft, x, y);
        }
    }

    private void renderCaption() {
        RadialEntry entry = RadialMenu.get(hovered);
        int ringX = Math.round(ringX());
        int ringY = Math.round(ringY());

        if (entry == null) {
            Draw.textCentered(minecraft, "Release to cancel", ringX, ringY - 4, Theme.TEXT_FAINT);
            return;
        }

        Draw.textCentered(minecraft, entry.label(), ringX, ringY - 8,
                entry.enabled() ? Theme.ACCENT : Theme.TEXT_DIM);
        Draw.textCentered(minecraft,
                Draw.ellipsize(minecraft, entry.hint(), (int) (INNER_RADIUS * 2) + 40),
                ringX, ringY + 2, Theme.TEXT_FAINT);
    }

    private float ringX() {
        return width / 2F + snapX;
    }

    private float ringY() {
        return height / 2F + snapY;
    }

    private float openProgress() {
        float progress = Math.min(1, (System.currentTimeMillis() - openedAt) / (float) OPEN_MILLIS);
        float remaining = 1 - progress;
        return 0.84F + 0.16F * (1 - remaining * remaining * remaining);
    }

    private static void drainMouseDeltas() {
        Mouse.getDX();
        Mouse.getDY();
    }
}
