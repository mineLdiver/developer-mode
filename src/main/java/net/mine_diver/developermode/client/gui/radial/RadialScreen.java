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
import java.util.Arrays;
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
 *   <li>Left button down, and you are playing. The mouse is the camera, the
 *       movement keys reach the player again, the ring stands out of the way,
 *       and letting go opens whatever the crosshair is on.
 * </ul>
 *
 * <p>So the world and the menu are never competing for the same movement, and
 * there is nothing to learn beyond which button is down. Letting go of the key
 * leaves, always, and never chooses anything: only the left button chooses, so
 * the key has one meaning everywhere.
 *
 * <p>The ring is the same size at the same angles every time it is up. Only the
 * middle answers what is out there, opening into a window when the crosshair
 * finds something and closing to a pinhole when it does not, so the menu holds
 * still while the world is what moves.
 *
 * <p>Two things respond to the stick, and they respond differently on purpose.
 * The glow in the hole tracks it continuously, so pushing into empty space
 * still feels connected. The ring itself ignores that and only steps, by a
 * fixed amount, once the push actually lands on a slot, so it reads as a
 * commitment rather than as drifting furniture.
 */
public final class RadialScreen extends DevScreen {
    private static final float INNER_RADIUS = 64;
    private static final float OUTER_RADIUS = 112;
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

    /** Ajar: the middle is a clear window, filling the ring's hole. */
    private static final float AJAR = 0.3F;
    /** GUI pixels the ring travels outward once aiming takes over. */
    private static final float APERTURE_TRAVEL = 130;
    /** What is left of the ring once aiming has taken it out of the way. */
    private static final float APERTURE_FADE = 0.12F;
    /** The pinhole the aperture closes down to. */
    private static final float HOLE_CLOSED = 6;

    // Sprung rather than eased, so the middle overshoots a little and settles.
    // It is the one part of this that reacts to the world on its own, and a
    // flinch is what makes that read as an answer rather than as a transition.
    private static final float APERTURE_STIFFNESS = 260;
    private static final float APERTURE_DAMPING = 22;
    /** Decay of the rim flash when what is out there changes. */
    private static final float PULSE_TAU_MILLIS = 190;

    /** How long one level takes to hand over to another. */
    private static final float TRANSITION_MILLIS = 200;
    /** How far the level being left flies out, and the one arriving starts in. */
    private static final float ZOOM = 0.45F;

    /** How far a slot stands out of the ring once the stick lands on it. */
    private static final float LIFT_DISTANCE = 5;
    private static final float LIFT_TAU_MILLIS = 55;

    /** Half width of the hairline that divides one slot from the next. */
    private static final float SPOKE_DEGREES = 0.45F;

    private static final int ICON_SIZE = 16;
    private static final int ICON_SIZE_LIFTED = 21;
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

    /** The level being left, while it is still on screen. */
    private RadialMenu outgoing;
    /** The slot that was taken to leave it, so it can be seen being taken. */
    private int takenSlot = -1;
    private boolean descending;
    private float transition = 1;

    /** How far each slot stands out, so the lift eases instead of snapping. */
    private final float[] lift = new float[RadialMenu.SLOTS];

    private float aperture;
    private float apertureVelocity;
    private boolean wasTargeted;
    private float pulse;

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
        outgoing = null;
        takenSlot = -1;
        transition = 1;
        Arrays.fill(lift, 0);
        aperture = 0;
        apertureVelocity = 0;
        wasTargeted = false;
        pulse = 0;
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

        updateAperture(elapsed);
        updateSnap(elapsed);
        updateTransition(elapsed);
        updateLift(elapsed);

        renderBackdrop();
        float grow = openProgress();
        renderHole(grow);

        // Going down zooms into the slot that was taken and grows the level
        // behind it; coming back up does the same in reverse, so which way you
        // went is legible from the movement alone.
        if (outgoing == null) {
            renderLevel(menu, grow, 1, hovered, true);
        } else {
            float eased = ease(transition);
            float leaving = descending ? 1 + ZOOM * eased : 1 - ZOOM * eased * 0.8F;
            float arriving = descending ? 1 - ZOOM + ZOOM * eased : 1 + ZOOM - ZOOM * eased;
            renderLevel(outgoing, grow * leaving, 1 - eased, descending ? takenSlot : -1, false);
            renderLevel(menu, grow * arriving, eased, -1, false);
        }

        if (!aiming) renderGlow();

        // One thing in the middle at a time. A hovered slot wins, because what
        // a click would do beats what is behind the window it would leave.
        if (hovered >= 0 || aperture < 0.08F) {
            renderCaption();
        } else {
            InspectRenderer.renderReadout(minecraft, width, height, help());
        }
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
                beginTransition(-1, false);
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
            beginTransition(hovered, true);
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

    private static boolean targeted() {
        return InspectMode.isBlockFocused() || InspectMode.focused() != null;
    }

    /**
     * Opens and closes the middle in answer to what is under the crosshair.
     *
     * <p>Only the middle. The ring keeps its size and its angles whatever is
     * out there, so nothing a passing cow does moves what a flick would hit,
     * and the aperture is free to answer the world because answering it costs
     * the menu nothing.
     */
    private void updateAperture(float elapsedMillis) {
        boolean nowTargeted = targeted();
        if (nowTargeted != wasTargeted) {
            wasTargeted = nowTargeted;
            pulse = 1;
        }
        pulse *= (float) Math.exp(-elapsedMillis / PULSE_TAU_MILLIS);

        float target = aiming ? 1 : nowTargeted ? AJAR : 0;

        // Seconds, and capped: a long frame must not hand the spring enough
        // energy to throw the aperture across its range in one step.
        float step = Math.min(elapsedMillis, 50) / 1000F;
        apertureVelocity += (target - aperture) * APERTURE_STIFFNESS * step;
        apertureVelocity -= apertureVelocity * APERTURE_DAMPING * step;
        aperture = Math.max(0, Math.min(1.2F, aperture + apertureVelocity * step));
    }

    private void beginTransition(int slot, boolean descend) {
        outgoing = menu;
        takenSlot = slot;
        descending = descend;
        transition = 0;
        Arrays.fill(lift, 0);
    }

    private void updateTransition(float elapsedMillis) {
        if (outgoing == null) return;

        transition += elapsedMillis / TRANSITION_MILLIS;
        if (transition >= 1) {
            transition = 1;
            outgoing = null;
            takenSlot = -1;
        }
    }

    /**
     * Eases each slot towards standing out or lying flat, rather than letting
     * the lift jump, so sweeping across the ring reads as one movement.
     */
    private void updateLift(float elapsedMillis) {
        float alpha = (float) (1 - Math.exp(-elapsedMillis / LIFT_TAU_MILLIS));
        for (int slot = 0; slot < RadialMenu.SLOTS; slot++) {
            float target = outgoing == null && slot == hovered ? LIFT_DISTANCE : 0;
            lift[slot] += (target - lift[slot]) * alpha;
        }
    }

    /** Fast then settling, which is what makes a hand over feel like one move. */
    private static float ease(float progress) {
        float remaining = 1 - progress;
        return 1 - remaining * remaining * remaining;
    }

    /** How far aiming has taken over, which is the only thing that moves the ring. */
    private float aimProgress() {
        return Math.max(0, (aperture - AJAR) / (1 - AJAR));
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

    /**
     * The clear window: a pinhole when there is nothing to see, the ring's own
     * hole when there is, and out past the ring once aiming takes over.
     */
    private float holeRadius() {
        float inside = Math.min(aperture, AJAR) / AJAR;
        float ringHole = INNER_RADIUS * openProgress();
        return HOLE_CLOSED + (ringHole - HOLE_CLOSED) * inside + APERTURE_TRAVEL * aimProgress();
    }

    /**
     * The iris, drawn under every level so it belongs to the screen rather
     * than to whichever level happens to be on it.
     */
    private void renderHole(float grow) {
        float push = aimProgress() * APERTURE_TRAVEL;
        float inner = INNER_RADIUS * grow + push;
        float visible = 1 - aimProgress() * (1 - APERTURE_FADE);
        float hole = holeRadius();

        // It shrinks rather than fading, which is what makes the middle read as
        // an iris instead of as something being turned off.
        if (hole < inner) {
            Draw.ring(ringX(), ringY(), hole, inner, 0, 360, fade(Theme.PANEL_SUNKEN, visible));
        }
        if (pulse > 0.01F) {
            Draw.ring(ringX(), ringY(), hole, hole + 2, 0, 360, fade(Theme.ACCENT, pulse * 0.8F));
        }
    }

    /**
     * One level of the menu at a given size and opacity, which is what lets two
     * of them be on screen at once while one hands over to the other.
     *
     * @param highlight the slot to show as taken, or -1
     * @param live      whether this is the level the stick is actually on
     */
    private void renderLevel(RadialMenu level, float scale, float alpha, int highlight, boolean live) {
        float visible = alpha * (1 - aimProgress() * (1 - APERTURE_FADE));
        if (visible <= 0.01F) return;

        float ringX = ringX();
        float ringY = ringY();
        float push = aimProgress() * APERTURE_TRAVEL;
        float inner = INNER_RADIUS * scale + push;
        float outer = OUTER_RADIUS * scale + push;
        double slice = 360.0 / RadialMenu.SLOTS;

        for (int slot = 0; slot < RadialMenu.SLOTS; slot++) {
            double middle = slot * slice;
            double from = middle - slice / 2 + SLOT_GAP_DEGREES / 2;
            double to = middle + slice / 2 - SLOT_GAP_DEGREES / 2;

            RadialEntry entry = level.get(slot);
            boolean filled = entry != null && entry.enabled();
            boolean selected = slot == highlight;
            int color = selected && filled ? Theme.ACCENT_FILL : entry != null ? Theme.PANEL : Theme.PANEL_SUNKEN;

            // Past the nominal edge on purpose, so a taken slot reads as coming
            // up out of the ring rather than just changing colour.
            float edge = outer + (live ? lift[slot] : 0);
            Draw.ring(ringX, ringY, inner, edge, from, to, fade(color, visible));
            if (selected && filled) {
                Draw.ring(ringX, ringY, edge - 2, edge, from, to, fade(Theme.ACCENT, visible));
            }
        }

        renderBoundaries(ringX, ringY, inner, outer, slice, visible);
        renderIcons(level, scale, visible, highlight, live);
    }

    /**
     * Rims and spokes. The ring is a dial, and a dial that shows where one
     * reading stops and the next begins is easier to aim at than a smooth one,
     * empty seats included.
     */
    private void renderBoundaries(float ringX, float ringY, float inner, float outer,
                                  double slice, float visible) {
        Draw.ring(ringX, ringY, inner, inner + 1, 0, 360, fade(Theme.BORDER, visible * 0.9F));
        Draw.ring(ringX, ringY, outer - 1, outer, 0, 360, fade(Theme.BORDER, visible * 0.5F));

        for (int slot = 0; slot < RadialMenu.SLOTS; slot++) {
            double edge = (slot + 0.5) * slice;
            Draw.ring(ringX, ringY, inner, outer, edge - SPOKE_DEGREES, edge + SPOKE_DEGREES,
                    fade(Theme.BORDER, visible * 0.75F));
        }
    }

    private void renderIcons(RadialMenu level, float scale, float visible, int highlight, boolean live) {
        // Items are drawn by the game's own renderer, which takes no opacity,
        // so they change over at the crossing point instead of fading.
        if (visible < 0.45F) return;

        float ringX = ringX();
        float ringY = ringY();
        float radius = (INNER_RADIUS + OUTER_RADIUS) / 2 * scale + aimProgress() * APERTURE_TRAVEL;
        double slice = 360.0 / RadialMenu.SLOTS;

        for (int slot = 0; slot < RadialMenu.SLOTS; slot++) {
            RadialEntry entry = level.get(slot);
            if (entry == null) continue;

            double radians = Math.toRadians(slot * slice);
            float reach = radius + (live ? lift[slot] / 2 : 0);
            int x = Math.round((float) (ringX + Math.sin(radians) * reach));
            int y = Math.round((float) (ringY - Math.cos(radians) * reach));
            entry.renderIcon(minecraft, x, y, slot == highlight ? ICON_SIZE_LIFTED : ICON_SIZE);
        }
    }

    private void renderGlow() {
        Draw.glow(
                width / 2F + pullX * GLOW_TRAVEL,
                height / 2F + pullY * GLOW_TRAVEL,
                GLOW_RADIUS, fade(Theme.GLOW, 1 - aperture));
    }

    private void renderCaption() {
        RadialEntry entry = menu.get(hovered);
        int ringX = Math.round(ringX());
        int ringY = Math.round(ringY());

        if (entry == null) {
            Draw.textCentered(minecraft,
                    Draw.ellipsize(minecraft, path(), (int) (INNER_RADIUS * 1.7F)),
                    ringX, ringY - 8, Theme.TEXT_DIM);
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

    /** Where in the tree this is, so depth is readable without going back up. */
    private String path() {
        if (trail.isEmpty()) return menu.title();

        StringBuilder path = new StringBuilder();
        for (RadialMenu level : trail) path.append(level.title()).append(" > ");
        return path.append(menu.title()).toString();
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
