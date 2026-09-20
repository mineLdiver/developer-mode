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
    /** Which side of a handover a level is on, which is what decides its sweep. */
    private enum Phase { SETTLED, LEAVING, ARRIVING }

    private static final float INNER_RADIUS = 64;
    private static final float OUTER_RADIUS = 112;
    private static final float SLOT_GAP_DEGREES = 3;

    /** GUI pixels of mouse travel for a fully pushed stick. */
    private static final float FULL_PULL = 40;
    /**
     * Travel needed before the push lands on a slot at all.
     *
     * <p>Generous, because the two ends of the push are not equally hard to
     * reach. A slot is everything past this and takes no aim at all, since a
     * flick that overshoots still lands on it. The middle is a place that has
     * to be returned to, and a small one would have to be aimed for.
     */
    private static final float DEAD_ZONE = 18;
    /**
     * How far back in the push has to come to let go of every slot, against
     * how far out it had to go to take one. Under one, so the edge of the dead
     * zone does not flicker when the push is sitting on it.
     */
    private static final float SLOT_RELEASE = 0.7F;

    /** Mouse travel to stick travel. Raise it for a twitchier ring. */
    private static final float SENSITIVITY = 1;

    /**
     * The glow's size. How far it travels is worked out from the hole rather
     * than set here, so it stays the same gesture whatever the ring becomes:
     * a full push puts it against the inside edge, whatever that edge is.
     */
    private static final float GLOW_RADIUS = 34;
    private static final float GLOW_MARGIN = 0.75F;

    /** The hard middle of the light, which is the pointer itself. */
    private static final float GLOW_CORE_RADIUS = 4;

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
    /** Decay of the flash when what is out there changes. */
    private static final float PULSE_TAU_MILLIS = 230;
    /** How far the ripple leaves the rim behind, and how it is built. */
    private static final float PULSE_TRAVEL = 30;
    private static final float PULSE_THICKNESS = 3;
    private static final int PULSE_BANDS = 4;

    /** How long one level takes to hand over to another. */
    private static final float TRANSITION_MILLIS = 420;
    /**
     * Degrees over which a slot hands over as the sweep reaches it. Wide
     * enough that several are mid change at once, so it reads as a wave
     * crossing the ring rather than as slots taking turns.
     */
    private static final float SWEEP_BAND_DEGREES = 55;
    /** Where the sweep has faded out by, as a fraction of the handover. */
    private static final float SWEEP_SPENT = 0.78F;
    /** Degrees of ring still lit behind the crest, and the steps it fades over. */
    private static final float SWEEP_TRAIL_DEGREES = 115;
    private static final int SWEEP_TRAIL_STEPS = 10;
    /** The bright edge right at the front of the wave. */
    private static final float SWEEP_CREST_DEGREES = 12;
    /** How bright the wave gets: the crest, and the light trailing it. */
    private static final float SWEEP_CREST_ALPHA = 0.95F;
    private static final float SWEEP_TRAIL_ALPHA = 0.5F;

    /**
     * How long the name of a level stays lit after arriving on it.
     *
     * <p>The wave says that something happened. This says what, and it outlasts
     * the wave on purpose, so the answer is still there once the eye has
     * finished following the movement that asked the question.
     */
    private static final float ARRIVAL_TAU_MILLIS = 420;

    /** Half width of the spur that ties the middle to the slot under the stick. */
    private static final double SPUR_DEGREES = 1.8;
    /** How far the spur reaches back from the ring towards the middle. */
    private static final float SPUR_REACH = 16;

    /** How far a slot stands out of the ring once the stick lands on it. */
    private static final float LIFT_DISTANCE = 5;
    private static final float LIFT_TAU_MILLIS = 55;

    /** Half width of the hairline that divides one slot from the next. */
    private static final float SPOKE_DEGREES = 0.45F;

    private static final int ICON_SIZE = 16;
    private static final int ICON_SIZE_LIFTED = 21;

    /** Sheets drawn behind a slot that has a level under it. */
    private static final int STACK_LAYERS = 2;
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
    /** The slot the handover runs from, so the sweep starts where you clicked. */
    private int takenSlot = -1;
    private boolean descending;
    private float transition = 1;
    /** Lit on arriving somewhere, and let go of slowly. */
    private float arrival;

    /** The slot taken at each level above, so going back converges on it. */
    private final Deque<Integer> trailSlots = new ArrayDeque<>();

    /** How far each slot stands out, so the lift eases instead of snapping. */
    private final float[] lift = new float[RadialMenu.MAX_SLOTS];



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
        trailSlots.clear();

        Mouse.setGrabbed(true);
        drainMouseDeltas();

        pullX = 0;
        pullY = 0;
        snapX = 0;
        snapY = 0;
        outgoing = null;
        takenSlot = -1;
        transition = 1;
        arrival = 0;
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
            renderLevel(menu, grow, hovered, true, Phase.SETTLED);
        } else {
            renderLevel(outgoing, grow, descending ? takenSlot : -1, false, Phase.LEAVING);
            renderLevel(menu, grow, -1, false, Phase.ARRIVING);
            renderSweep(grow);
        }

        if (!aiming) renderGlow(grow);

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
                beginTransition(trailSlots.removeLast(), false);
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
            trailSlots.addLast(hovered);
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
        arrival = 1;
        Arrays.fill(lift, 0);
    }

    private void updateTransition(float elapsedMillis) {
        arrival *= (float) Math.exp(-elapsedMillis / ARRIVAL_TAU_MILLIS);
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
        for (int slot = 0; slot < RadialMenu.MAX_SLOTS; slot++) {
            float target = slot == hovered ? LIFT_DISTANCE : 0;
            lift[slot] += (target - lift[slot]) * alpha;
        }
    }

    /** Fast then settling, which is what makes a hand over feel like one move. */
    private static float ease(float progress) {
        float remaining = 1 - progress;
        return 1 - remaining * remaining * remaining;
    }

    /**
     * The same idea, leaned on harder, for the wave that crosses the ring.
     *
     * <p>A quarter turn of a sine, so it gives up its speed evenly the whole
     * way across rather than spending most of it at the start. Curves that
     * fall away sharply are done before they look like they are slowing, which
     * over a distance this large reads as stopping rather than as settling.
     */
    private static float easeWave(float progress) {
        return (float) Math.sin(progress * Math.PI / 2);
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
        double targetAngle = hovered < 0 ? 0 : Math.toRadians(menu.angleOf(hovered));
        float targetX = hovered < 0 ? 0 : (float) Math.sin(targetAngle) * SNAP_DISTANCE;
        float targetY = hovered < 0 ? 0 : (float) -Math.cos(targetAngle) * SNAP_DISTANCE;

        float alpha = (float) (1 - Math.exp(-elapsedMillis / SNAP_TAU_MILLIS));
        snapX += (targetX - snapX) * alpha;
        snapY += (targetY - snapY) * alpha;
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
        // The rim takes the light, and a ripple leaves it and widens out. A
        // single thin band at the edge is the sort of thing that is only seen
        // once somebody says it is there.
        if (pulse > 0.01F) {
            Draw.ring(ringX(), ringY(), hole, hole + PULSE_THICKNESS, 0, 360,
                    fade(Theme.ACCENT, pulse));

            float travelled = (1 - pulse) * PULSE_TRAVEL;
            for (int band = 0; band < PULSE_BANDS; band++) {
                float edge = hole + travelled - band * PULSE_THICKNESS;
                if (edge <= hole + PULSE_THICKNESS) break;
                Draw.ring(ringX(), ringY(), edge - PULSE_THICKNESS, edge, 0, 360,
                        fade(Theme.ACCENT, pulse * (1 - band / (float) PULSE_BANDS) * 0.8F));
            }
        }
    }

    /**
     * One level of the menu at a given size and opacity, which is what lets two
     * of them be on screen at once while one hands over to the other.
     *
     * @param highlight the slot to show as taken, or -1
     * @param live      whether this is the level the stick is actually on
     */
    private void renderLevel(RadialMenu level, float scale, int highlight, boolean live, Phase phase) {
        float base = 1 - aimProgress() * (1 - APERTURE_FADE);
        if (base <= 0.01F) return;

        float ringX = ringX();
        float ringY = ringY();
        float push = aimProgress() * APERTURE_TRAVEL;
        float inner = INNER_RADIUS * scale + push;
        float outer = OUTER_RADIUS * scale + push;
        double slice = level.slice();

        for (int slot = 0; slot < level.size(); slot++) {
            float visible = base * slotAlpha(level, slot, phase);
            if (visible <= 0.01F) continue;

            double middle = level.angleOf(slot);
            // A level of one is the whole ring, and a gap in it would be a
            // notch cut out of a circle rather than a division between slots.
            double gap = level.size() < 2 ? 0 : SLOT_GAP_DEGREES;
            double from = middle - slice / 2 + gap / 2;
            double to = middle + slice / 2 - gap / 2;

            RadialEntry entry = level.get(slot);
            boolean enabled = entry.enabled();
            boolean selected = slot == highlight;
            int color = !enabled ? Theme.PANEL_SUNKEN : selected ? Theme.ACCENT_FILL : Theme.PANEL;

            // Past the nominal edge on purpose, so a taken slot reads as coming
            // up out of the ring rather than just changing colour.
            float edge = outer + (live ? lift[slot] : 0);
            Draw.ring(ringX, ringY, inner, edge, from, to, fade(color, visible));
            if (selected && enabled) {
                Draw.ring(ringX, ringY, edge - 2, edge, from, to, fade(Theme.ACCENT, visible));
            }
            if (entry.submenu() != null) {
                renderStack(ringX, ringY, edge, middle, slice, visible);
            }
        }

        renderBoundaries(level, ringX, ringY, inner, outer, base * levelAlpha(phase));
        if (live) renderSpur(scale, highlight);
        renderIcons(level, scale, highlight, live, phase);
    }

    /**
     * Sheets behind a slot that has a level under it.
     *
     * <p>A slot that opens another ring does not finish what you started, and
     * nothing else about it says so. Stacking it says there is more of it the
     * same way a pile of paper does, and it is readable before the click
     * rather than after.
     */
    private void renderStack(float ringX, float ringY, float edge, double middle,
                             double slice, float visible) {
        for (int layer = 1; layer <= STACK_LAYERS; layer++) {
            double inset = SLOT_GAP_DEGREES / 2 + layer * 3.0;
            float at = edge + layer * 3 - 1;
            Draw.ring(ringX, ringY, at, at + 1.5F,
                    middle - slice / 2 + inset, middle + slice / 2 - inset,
                    fade(layer == 1 ? Theme.TEXT_DIM : Theme.TEXT_FAINT, visible));
        }
    }

    /**
     * How far through the handover a given slot is.
     *
     * <p>The wave starts at the slot that was clicked and runs both ways
     * around the ring, so what happens next radiates out of what you chose.
     * Going back anchors at the far side instead, so the ring closes into the
     * slot you came from.
     */
    private float slotAlpha(RadialMenu level, int slot, Phase phase) {
        if (phase == Phase.SETTLED) return 1;

        double away = Math.abs(level.angleOf(slot) - sweepAnchor()) % 360;
        if (away > 180) away = 360 - away;

        float front = easeWave(transition) * 180;
        float progress = (float) ((front - away) / SWEEP_BAND_DEGREES);
        return phase == Phase.ARRIVING
                ? Math.max(0, Math.min(1, progress))
                : Math.max(0, Math.min(1, 1 - progress));
    }

    /** One number for the parts of a level that are not per slot. */
    private float levelAlpha(Phase phase) {
        if (phase == Phase.SETTLED) return 1;
        float eased = easeWave(transition);
        return phase == Phase.ARRIVING ? eased : 1 - eased;
    }

    /**
     * The pulse itself: light running out of the chosen slot in both
     * directions, through the ring rather than along its edges, spent by the
     * time it has crossed.
     *
     * <p>Brightest at the front and trailing off behind, because a band of one
     * colour filling an arc reads as an arc being filled. What makes it a wave
     * is that the leading edge is the bright part.
     */
    private void renderSweep(float grow) {
        if (takenSlot < 0) return;

        float strength = 1 - Math.max(0, (transition - SWEEP_SPENT) / (1 - SWEEP_SPENT));
        if (strength <= 0.01F) return;

        float push = aimProgress() * APERTURE_TRAVEL;
        float inner = INNER_RADIUS * grow + push;
        float outer = OUTER_RADIUS * grow + push;
        double anchor = sweepAnchor();
        float front = easeWave(transition) * 180;
        float step = SWEEP_TRAIL_DEGREES / SWEEP_TRAIL_STEPS;

        for (int i = 0; i < SWEEP_TRAIL_STEPS; i++) {
            double lead = front - i * step;
            if (lead <= 0) break;
            double tail = Math.max(0, front - (i + 1) * step);
            bothWays(anchor, tail, lead, inner, outer,
                    fade(Theme.ACCENT, strength * SWEEP_TRAIL_ALPHA
                            * (1 - i / (float) SWEEP_TRAIL_STEPS)));
        }

        if (front > 0) {
            bothWays(anchor, Math.max(0, front - SWEEP_CREST_DEGREES), front, inner, outer,
                    fade(Theme.ACCENT, strength * SWEEP_CREST_ALPHA));
        }
    }

    /** The two halves of the wave, mirrored about where it started. */
    private void bothWays(double anchor, double from, double to,
                          float inner, float outer, int argb) {
        Draw.ring(ringX(), ringY(), inner, outer, anchor + from, anchor + to, argb);
        Draw.ring(ringX(), ringY(), inner, outer, anchor - to, anchor - from, argb);
    }

    /**
     * Where the wave starts: the slot that was clicked on the way down, and the
     * far side of the ring on the way back, so going back closes into the slot
     * it came from rather than opening out of it.
     */
    private double sweepAnchor() {
        // The slot that was taken belongs to the level being left, so its angle
        // has to be asked of that level rather than of the one that replaced it.
        RadialMenu source = outgoing == null ? menu : outgoing;
        return source.angleOf(takenSlot) + (descending ? 0 : 180);
    }

    /**
     * A spoke of light from the middle out to the slot under the stick.
     *
     * <p>The ring is wide enough that what a slot is called and what it looks
     * like sit a long way apart, and the caption is in the middle. This ties
     * the two ends of that together, and it has room to exist only while the
     * aperture is shut, which is exactly when the caption is the thing being
     * read.
     */
    private void renderSpur(float grow, int highlight) {
        if (highlight < 0) return;

        float reach = lift[highlight] / LIFT_DISTANCE;
        if (reach <= 0.05F) return;

        float inner = INNER_RADIUS * grow + aimProgress() * APERTURE_TRAVEL;
        float from = Math.max(holeRadius(), inner - SPUR_REACH * reach);
        if (inner - from < 1) return;

        double middle = menu.angleOf(highlight);
        Draw.ring(ringX(), ringY(), from, inner, middle - SPUR_DEGREES, middle + SPUR_DEGREES,
                fade(Theme.ACCENT, 0.8F * reach));
    }

    /**
     * Rims and spokes. The ring is a dial, and a dial that shows where one
     * reading stops and the next begins is easier to aim at than a smooth one,
     * empty seats included.
     */
    private void renderBoundaries(RadialMenu level, float ringX, float ringY,
                                  float inner, float outer, float visible) {
        Draw.ring(ringX, ringY, inner, inner + 1, 0, 360, fade(Theme.BORDER, visible * 0.9F));
        Draw.ring(ringX, ringY, outer - 1, outer, 0, 360, fade(Theme.BORDER, visible * 0.5F));

        // Nothing to divide when a level holds one thing, and the whole ring is
        // that thing.
        if (level.size() < 2) return;

        for (int slot = 0; slot < level.size(); slot++) {
            double edge = level.angleOf(slot) + level.slice() / 2;
            Draw.ring(ringX, ringY, inner, outer, edge - SPOKE_DEGREES, edge + SPOKE_DEGREES,
                    fade(Theme.BORDER, visible * 0.75F));
        }
    }

    private void renderIcons(RadialMenu level, float scale, int highlight, boolean live, Phase phase) {
        float ringX = ringX();
        float ringY = ringY();
        float radius = (INNER_RADIUS + OUTER_RADIUS) / 2 * scale + aimProgress() * APERTURE_TRAVEL;

        for (int slot = 0; slot < level.size(); slot++) {
            RadialEntry entry = level.get(slot);
            if (entry == null) continue;
            // Items are drawn by the game's own renderer, which takes no
            // opacity, so each one changes over as the wave reaches its slot.
            if (slotAlpha(level, slot, phase) < 0.5F) continue;

            double radians = Math.toRadians(level.angleOf(slot));
            float reach = radius + (live ? lift[slot] / 2 : 0);
            int x = Math.round((float) (ringX + Math.sin(radians) * reach));
            int y = Math.round((float) (ringY - Math.cos(radians) * reach));
            entry.renderIcon(minecraft, x, y, slot == highlight ? ICON_SIZE_LIFTED : ICON_SIZE);
        }
    }

    /**
     * Which entry the push is pointing at, or -1 for none.
     *
     * <p>Letting go of one needs the push a little further back in than taking
     * it did, so a push resting on the edge of the dead zone does not flicker
     * between an entry and nothing.
     */
    private int slotUnderPull() {
        if (menu.size() == 0) return -1;

        float distance = (float) Math.hypot(pullX, pullY);
        float edge = hovered >= 0 ? DEAD_ZONE * SLOT_RELEASE : DEAD_ZONE;
        if (distance < edge) return -1;

        double degrees = pushAngle();
        if (degrees < 0) degrees += 360;

        double slice = menu.slice();
        int slot = (int) Math.floor((degrees + slice / 2) / slice) % menu.size();
        return menu.get(slot) == null ? -1 : slot;
    }

    /**
     * The light in the hole, which is the only thing that answers the push
     * before the push has reached anything.
     *
     * <p>Its reach is worked out from the hole rather than set by hand, so a
     * full push puts it against the inside edge whatever that edge becomes.
     */
    private void renderGlow(float grow) {
        float rim = INNER_RADIUS * grow + aimProgress() * APERTURE_TRAVEL;
        float travel = Math.max(1, rim - GLOW_RADIUS * GLOW_MARGIN) / FULL_PULL;
        float x = ringX() + pullX * travel;
        float y = ringY() + pullY * travel;
        int color = fade(Theme.GLOW, 1 - aperture);

        Draw.glow(x, y, GLOW_RADIUS, color);
        // Twice over once the push has landed on something. The light is
        // additive, so a second pass is the same light again, and the middle
        // brightens the moment a flick has somewhere to go.
        if (hovered >= 0 && outgoing == null) Draw.glow(x, y, GLOW_RADIUS, color);
        // Solid rather than soft, so the push has a point and not just a haze.
        Draw.ring(x, y, 0, GLOW_CORE_RADIUS, 0, 360, fade(Theme.GLOW_CORE, 1 - aperture));
    }

    /** Degrees clockwise from straight up that the push is pointing. */
    private double pushAngle() {
        return Math.toDegrees(Math.atan2(pullX, -pullY));
    }

    private void renderCaption() {
        RadialEntry entry = menu.get(hovered);
        int ringX = Math.round(ringX());
        int ringY = Math.round(ringY());

        if (entry == null) {
            Draw.textCentered(minecraft,
                    Draw.ellipsize(minecraft, path(), (int) (INNER_RADIUS * 1.7F)),
                    ringX, ringY - 8, blend(Theme.TEXT_DIM, Theme.ACCENT, arrival));
            Draw.textCentered(minecraft,
                    trail.isEmpty() ? "hold left to look" : "right click to go back",
                    ringX, ringY + 2, Theme.TEXT_FAINT);
            return;
        }

        // A slot that leads somewhere says so twice: stacked in the ring, and
        // carried on its name, since the name is what you read before clicking.
        RadialMenu submenu = entry.submenu();
        String label = submenu == null ? entry.label() : entry.label() + "  >";
        String hint = submenu == null
                ? entry.hint()
                : entry.hint() + "   (" + submenu.size() + ")";

        Draw.textCentered(minecraft, label, ringX, ringY - 8,
                entry.enabled() ? Theme.ACCENT : Theme.TEXT_DIM);
        Draw.textCentered(minecraft,
                Draw.ellipsize(minecraft, hint, (int) (INNER_RADIUS * 2) + 40),
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

    /** Mixes one colour towards another, keeping the alpha of the first. */
    private static int blend(int from, int to, float amount) {
        float keep = 1 - amount;
        int red = Math.round(((from >> 16) & 0xFF) * keep + ((to >> 16) & 0xFF) * amount);
        int green = Math.round(((from >> 8) & 0xFF) * keep + ((to >> 8) & 0xFF) * amount);
        int blue = Math.round((from & 0xFF) * keep + (to & 0xFF) * amount);
        return (from & 0xFF000000) | (red << 16) | (green << 8) | blue;
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
