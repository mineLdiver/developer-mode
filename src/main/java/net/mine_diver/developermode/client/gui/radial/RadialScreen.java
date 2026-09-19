package net.mine_diver.developermode.client.gui.radial;

import net.mine_diver.developermode.client.DeveloperModeClient;
import net.mine_diver.developermode.client.gui.Blur;
import net.mine_diver.developermode.client.gui.DevScreen;
import net.mine_diver.developermode.client.gui.Draw;
import net.mine_diver.developermode.client.gui.Theme;
import net.mine_diver.developermode.client.inspect.InspectMode;
import net.mine_diver.developermode.client.inspect.InspectRenderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.player.ClientPlayerEntity;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The whole mod, in one hold.
 *
 * <p>It always sits in the middle of the screen. There is no pointer: the mouse
 * is grabbed while the ring is up, and it has exactly two meanings.
 *
 * <ul>
 *   <li>Left button up, and movement is raw deflection, like pushing a stick.
 *       Where the stick points is what a click would take.
 *   <li>Left button down, and movement is the camera. The ring opens out of the
 *       way, the crosshair is the aim, and letting go opens whatever is under
 *       it.
 * </ul>
 *
 * <p>So the world and the menu are never competing for the same movement, and
 * there is nothing to learn beyond which button is down. Letting go of the key
 * leaves, always, and never chooses anything: only the left button chooses, so
 * the key has one meaning everywhere.
 *
 * <p>Two things respond to the stick, and they respond differently on purpose.
 * The glow in the hole tracks it continuously, so pushing into empty space
 * still feels connected. The ring itself ignores that and only steps, by a
 * fixed amount, once the push actually lands on a slot, so it reads as a
 * commitment rather than as drifting furniture.
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

    /** Ajar: open enough to read what is out there without giving up the ring. */
    private static final float AJAR = 0.3F;
    /** GUI pixels the ring travels outward at a full aperture. */
    private static final float APERTURE_TRAVEL = 130;
    private static final float APERTURE_TAU_MILLIS = 70;
    /** What is left of the ring once the aperture is wide. */
    private static final float APERTURE_FADE = 0.12F;
    /** How far the sharp window fades back into the blur, in GUI pixels. */
    private static final float HOLE_FEATHER = 14;

    private final Screen returnTo;

    /** The levels above the one on screen, nearest last. */
    private final Deque<RadialMenu> trail = new ArrayDeque<>();
    private RadialMenu menu;

    private float pullX;
    private float pullY;
    private float snapX;
    private float snapY;
    private int hovered = -1;
    private long openedAt;
    private long lastFrameAt;

    /** Whether the left button is down over the middle, so the mouse is the camera. */
    private boolean aiming;
    /** Set by the right button, so the left one can be let go of harmlessly. */
    private boolean aimCancelled;

    private float aperture;
    private float apertureRest;
    private boolean sampledContext;

    private RadialScreen(Screen returnTo) {
        this.returnTo = returnTo;
    }

    public static void open(Screen returnTo) {
        DeveloperModeClient.minecraft().setScreen(new RadialScreen(returnTo));
    }

    @Override
    public void init() {
        menu = RadialMenu.root();
        trail.clear();

        Mouse.setGrabbed(true);
        drainMouseDeltas();

        pullX = 0;
        pullY = 0;
        snapX = 0;
        snapY = 0;
        aperture = 0;
        apertureRest = 0;
        sampledContext = false;
        openedAt = System.currentTimeMillis();
        lastFrameAt = openedAt;

        InspectMode.enter();
    }

    @Override
    public void removed() {
        releaseMovement();
        InspectMode.exit();
        Mouse.setGrabbed(false);
        drainMouseDeltas();
    }

    @Override
    public void render(int mouseX, int mouseY, float delta) {
        Draw.resetScissor();

        long now = System.currentTimeMillis();
        float elapsed = now - lastFrameAt;
        lastFrameAt = now;

        // Polled here rather than off an event so the ring closes on the frame
        // the hold ends instead of on the next twentieth of a second.
        if (!Keyboard.isKeyDown(DeveloperModeClient.OPEN_KEY.code)) {
            close();
            return;
        }

        // The crosshair is the aim, so what is under it has to be worked out
        // from the middle of the viewport.
        InspectMode.aimAt(minecraft.displayWidth / 2, minecraft.displayHeight / 2);

        if (aiming) {
            applyLook();
            applyMovement();
            hovered = -1;
        } else {
            updatePull();
            hovered = slotUnderPull();
        }

        sampleContext();
        updateAperture(elapsed);
        updateSnap(elapsed);

        renderBackdrop();
        float grow = openProgress();
        renderRing(grow);
        if (!aiming) renderGlow();
        renderIcons(grow);

        if (aperture > 0.02F) InspectRenderer.renderReadout(minecraft, width, height, help());
        renderCaption();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (button == 0) {
            if (hovered >= 0) {
                activate(menu.get(hovered));
            } else {
                aiming = true;
                aimCancelled = false;
                drainMouseDeltas();
            }
            return;
        }

        if (button == 1) {
            // While aiming this disarms the release rather than ending it, so
            // there is always a way to put the button down without choosing.
            if (aiming) {
                aimCancelled = true;
            } else if (!trail.isEmpty()) {
                menu = trail.removeLast();
                resetPush();
            } else {
                close();
            }
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int button) {
        if (button != 0 || !aiming) return;

        aiming = false;
        releaseMovement();
        resetPush();
        if (!aimCancelled) InspectMode.pick();
        aimCancelled = false;
    }

    @Override
    protected void keyPressed(char character, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) close();
    }

    private void activate(RadialEntry entry) {
        if (entry == null || !entry.enabled()) return;

        RadialMenu submenu = entry.submenu();
        if (submenu != null) {
            trail.addLast(menu);
            menu = submenu;
            resetPush();
            return;
        }

        entry.perform(returnTo);
    }

    private void close() {
        minecraft.setScreen(returnTo);
    }

    /** Puts the stick back in the middle, so a new level starts from centre. */
    private void resetPush() {
        pullX = 0;
        pullY = 0;
        hovered = -1;
        drainMouseDeltas();
    }

    /**
     * Turns the head exactly as the game does when no screen is up, since
     * nothing else is driving it while this one is.
     */
    private void applyLook() {
        if (minecraft.player == null) return;

        GameOptions options = minecraft.options;
        float step = options.mouseSensitivity * 0.6F + 0.2F;
        float scale = step * step * step * 8;
        float invert = options.invertYMouse ? -1 : 1;

        minecraft.player.changeLookDirection(
                Mouse.getDX() * scale, Mouse.getDY() * scale * invert);
    }

    /**
     * Hands the movement keys back to the player while the button is down.
     *
     * <p>Opening any screen releases them, and nothing puts them back while one
     * is up, so they are read straight off the keyboard here. Setting the same
     * state twice costs nothing, which is why this can poll rather than having
     * to track presses and releases.
     */
    private void applyMovement() {
        ClientPlayerEntity player = minecraft.player;
        if (player == null) return;

        GameOptions options = minecraft.options;
        feed(player, options.forwardKey);
        feed(player, options.backKey);
        feed(player, options.leftKey);
        feed(player, options.rightKey);
        feed(player, options.jumpKey);
        feed(player, options.sneakKey);
    }

    private static void feed(ClientPlayerEntity player, KeyBinding binding) {
        player.updateKey(binding.code, Keyboard.isKeyDown(binding.code));
    }

    /** Otherwise whatever was held when the button came up stays held. */
    private void releaseMovement() {
        if (minecraft.player != null) minecraft.player.releaseAllKeys();
    }

    /**
     * Decides once, on the way in, whether the ring starts ajar.
     *
     * <p>Only once: an aperture that answered the world would open under the
     * user's hand whenever something wandered into view.
     */
    private void sampleContext() {
        if (sampledContext) return;
        // The aim is worked out during the world render, so there is nothing to
        // read on the frame that opened the screen.
        if (System.currentTimeMillis() == openedAt) return;

        sampledContext = true;
        apertureRest = targeted() ? AJAR : 0;
    }

    private static boolean targeted() {
        return InspectMode.isBlockFocused() || InspectMode.focused() != null;
    }

    private void updateAperture(float elapsedMillis) {
        float target = aiming ? 1 : apertureRest;
        float alpha = (float) (1 - Math.exp(-elapsedMillis / APERTURE_TAU_MILLIS));
        aperture += (target - aperture) * alpha;
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
        return menu.get(slot) == null ? -1 : slot;
    }

    /**
     * Blurred and dimmed everywhere except the hole, which is the frame exactly
     * as the world drew it. The hole is the ring's own hole, so opening the
     * aperture is what widens the window rather than a second thing to tune.
     */
    @Override
    protected void renderBackdrop() {
        Draw.resetScissor();
        Blur.render(minecraft, true);
        Draw.rect(0, 0, width, height, Theme.SCRIM);
        Blur.punch(minecraft, holeRadius(), HOLE_FEATHER);
    }

    private float holeRadius() {
        return INNER_RADIUS * openProgress() + aperture * APERTURE_TRAVEL;
    }

    private void renderRing(float grow) {
        float ringX = ringX();
        float ringY = ringY();
        float push = aperture * APERTURE_TRAVEL;
        float inner = holeRadius();
        float outer = OUTER_RADIUS * grow + push;
        float visible = 1 - aperture * (1 - APERTURE_FADE);

        // A faint disc behind the hole, so the caption stays readable over
        // whatever the world happens to be doing. It goes with the aperture,
        // since the point of opening up is to see through it.
        Draw.ring(ringX, ringY, 0, INNER_RADIUS * grow, 0, 360,
                fade(Theme.PANEL_SUNKEN, 1 - aperture));

        double slice = 360.0 / RadialMenu.SLOTS;
        for (int slot = 0; slot < RadialMenu.SLOTS; slot++) {
            double middle = slot * slice;
            double from = middle - slice / 2 + SLOT_GAP_DEGREES / 2;
            double to = middle + slice / 2 - SLOT_GAP_DEGREES / 2;

            RadialEntry entry = menu.get(slot);
            boolean filled = entry != null && entry.enabled();
            boolean selected = slot == hovered;
            int color = selected && filled ? Theme.ACCENT_FILL : entry != null ? Theme.PANEL : Theme.PANEL_SUNKEN;

            Draw.ring(ringX, ringY, inner, outer, from, to, fade(color, visible));
            if (selected && filled) {
                Draw.ring(ringX, ringY, outer - 2, outer, from, to, fade(Theme.ACCENT, visible));
            }
        }
    }

    private void renderGlow() {
        Draw.glow(
                width / 2F + pullX * GLOW_TRAVEL,
                height / 2F + pullY * GLOW_TRAVEL,
                GLOW_RADIUS, fade(Theme.GLOW, 1 - aperture));
    }

    private void renderIcons(float grow) {
        if (aperture > 0.75F) return;

        float ringX = ringX();
        float ringY = ringY();
        float radius = (INNER_RADIUS + OUTER_RADIUS) / 2 * grow + aperture * APERTURE_TRAVEL;
        double slice = 360.0 / RadialMenu.SLOTS;

        for (int slot = 0; slot < RadialMenu.SLOTS; slot++) {
            RadialEntry entry = menu.get(slot);
            if (entry == null) continue;

            double radians = Math.toRadians(slot * slice);
            int x = Math.round((float) (ringX + Math.sin(radians) * radius));
            int y = Math.round((float) (ringY - Math.cos(radians) * radius));
            entry.renderIcon(minecraft, x, y);
        }
    }

    private void renderCaption() {
        if (aperture > 0.02F) return;

        RadialEntry entry = menu.get(hovered);
        int ringX = Math.round(ringX());
        int ringY = Math.round(ringY());

        if (entry == null) {
            Draw.textCentered(minecraft, menu.title(), ringX, ringY - 8, Theme.TEXT_DIM);
            Draw.textCentered(minecraft,
                    trail.isEmpty() ? "hold left to look" : "right click to go back",
                    ringX, ringY + 2, Theme.TEXT_FAINT);
            return;
        }

        Draw.textCentered(minecraft, entry.label(), ringX, ringY - 8,
                entry.enabled() ? Theme.ACCENT : Theme.TEXT_DIM);
        Draw.textCentered(minecraft,
                Draw.ellipsize(minecraft, entry.hint(), (int) (INNER_RADIUS * 2) + 40),
                ringX, ringY + 2, Theme.TEXT_FAINT);
    }

    private String help() {
        if (!aiming) return "hold left click to look around";
        return aimCancelled
                ? "cancelled, let go safely"
                : targeted() ? "let go to open    right click to cancel" : "let go to come back";
    }

    /** Scales a colour's alpha, leaving the colour itself alone. */
    private static int fade(int argb, float factor) {
        int alpha = Math.round(((argb >>> 24) & 0xFF) * Math.max(0, Math.min(1, factor)));
        return (alpha << 24) | (argb & 0xFFFFFF);
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
