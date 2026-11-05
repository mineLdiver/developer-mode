package net.mine_diver.developermode.client.gui.window;

import net.mine_diver.developermode.client.Lang;
import net.mine_diver.developermode.client.Workbench;
import net.mine_diver.developermode.client.gui.Button;
import net.mine_diver.developermode.client.gui.Draw;
import net.mine_diver.developermode.client.gui.Slider;
import net.mine_diver.developermode.client.gui.Theme;
import net.mine_diver.developermode.client.gui.composer.ComposerScreen;
import net.mine_diver.developermode.client.gui.composer.DevWindow;
import net.mine_diver.developermode.feature.world.transform.Parameter;
import net.mine_diver.developermode.feature.world.transform.Stack;
import net.mine_diver.developermode.feature.world.transform.Threshold;
import net.mine_diver.developermode.feature.world.transform.Transform;
import net.mine_diver.developermode.feature.world.transform.VortexWarp;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The transforms laid over the generator's noise, in order, each with its own
 * sliders and switchable on its own.
 *
 * <p>A preview open beside this builds again whenever something here settles
 * on a new value, so the change can be watched as it is made.
 */
public final class TransformWindow extends DevWindow {
    private static final int HEADER_HEIGHT = 13;
    private static final int GAP = 2;
    private static final int SPACING = 5;
    private static final int SMALL = 12;
    private static final int FOOTER_HEIGHT = Button.HEIGHT + 4;

    private final Button addVortexButton = new Button("gui.developermode.transforms.add.vortex");
    private final Button addThresholdButton = new Button("gui.developermode.transforms.add.threshold");
    private final Map<Transform, Row> rows = new IdentityHashMap<>();
    private int scroll;

    public TransformWindow() {
        super("gui.developermode.transforms.title", 210, 240);
    }

    public static void open() {
        ComposerScreen.reveal(TransformWindow.class, TransformWindow::new);
    }

    @Override
    protected void renderContent(Minecraft minecraft, int mouseX, int mouseY, float delta, boolean focused) {
        Stack stack = Workbench.STACK;
        rows.keySet().retainAll(stack.transforms());

        int listBottom = contentY() + contentHeight() - FOOTER_HEIGHT;
        int contentHeight = 0;
        for (Transform transform : stack.transforms()) contentHeight += row(transform).height();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight - (listBottom - contentY()))));

        Draw.pushScissor(minecraft, contentX(), contentY(), contentWidth(), listBottom - contentY());
        int y = contentY() - scroll;
        List<Transform> transforms = stack.transforms();
        for (int i = 0; i < transforms.size(); i++) {
            Row row = row(transforms.get(i));
            row.render(minecraft, contentX(), y, contentWidth(), mouseX, mouseY, i, transforms.size());
            y += row.height();
        }
        Draw.popScissor();

        if (transforms.isEmpty()) {
            for (int i = 0; i < 2; i++) {
                String line = Lang.get(i == 0 ? "gui.developermode.transforms.empty" : "gui.developermode.transforms.empty.hint");
                Draw.textCentered(minecraft, line, contentX() + contentWidth() / 2, contentY() + 20 + i * 11, Theme.TEXT_FAINT);
            }
        }

        int footerY = listBottom + 4;
        int half = (contentWidth() - GAP) / 2;
        addVortexButton.bounds(contentX(), footerY, half);
        addVortexButton.render(minecraft, mouseX, mouseY);
        addThresholdButton.bounds(contentX() + half + GAP, footerY, half);
        addThresholdButton.render(minecraft, mouseX, mouseY);
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) {
        Stack stack = Workbench.STACK;
        if (addVortexButton.contains(mouseX, mouseY)) {
            stack.add(new VortexWarp());
            return;
        }
        if (addThresholdButton.contains(mouseX, mouseY)) {
            stack.add(new Threshold());
            return;
        }
        if (mouseY >= contentY() + contentHeight() - FOOTER_HEIGHT) return;
        for (Transform transform : new ArrayList<>(stack.transforms())) {
            if (row(transform).click(stack, mouseX, mouseY)) return;
        }
    }

    @Override
    public void mouseScrolled(int mouseX, int mouseY, int direction) {
        scroll -= direction * 24;
    }

    private Row row(Transform transform) {
        return rows.computeIfAbsent(transform, Row::new);
    }

    /** One transform: a header to switch, move and remove it, and a slider for each of its numbers. */
    private static final class Row {
        private final Transform transform;
        private final Button toggle = new Button("gui.developermode.transforms.on");
        private final Button up = new Button("gui.developermode.transforms.up");
        private final Button down = new Button("gui.developermode.transforms.down");
        private final Button remove = new Button("gui.developermode.transforms.remove");
        private final List<Slider> sliders = new ArrayList<>();

        Row(Transform transform) {
            this.transform = transform;
            for (Parameter parameter : transform.parameters()) sliders.add(new Slider(parameter));
        }

        int height() {
            return HEADER_HEIGHT + sliders.size() * (Slider.HEIGHT + GAP) + SPACING;
        }

        void render(Minecraft minecraft, int x, int y, int width, int mouseX, int mouseY, int index, int count) {
            Draw.rect(x, y, x + width, y + HEADER_HEIGHT - 1, Theme.TITLE_BAR);
            toggle.key = transform.enabled() ? "gui.developermode.transforms.on" : "gui.developermode.transforms.off";
            toggle.toggled = transform.enabled();
            toggle.bounds(x, y, 24);
            toggle.render(minecraft, mouseX, mouseY);

            remove.bounds(x + width - SMALL, y, SMALL);
            down.bounds(remove.x - SMALL - 1, y, SMALL);
            up.bounds(down.x - SMALL - 1, y, SMALL);
            up.enabled = index > 0;
            down.enabled = index < count - 1;
            up.render(minecraft, mouseX, mouseY);
            down.render(minecraft, mouseX, mouseY);
            remove.render(minecraft, mouseX, mouseY);

            int nameLeft = x + 28;
            Draw.text(minecraft, Draw.ellipsize(minecraft, Lang.get(transform.key()), up.x - nameLeft - 2), nameLeft, y + 2,
                    transform.enabled() ? Theme.TEXT : Theme.TEXT_FAINT);

            int sliderY = y + HEADER_HEIGHT;
            for (Slider slider : sliders) {
                slider.bounds(x, sliderY, width);
                slider.render(minecraft, mouseX, mouseY);
                sliderY += Slider.HEIGHT + GAP;
            }
        }

        /** @return whether the click was on this */
        boolean click(Stack stack, int mouseX, int mouseY) {
            if (toggle.contains(mouseX, mouseY)) {
                transform.setEnabled(!transform.enabled());
                return true;
            }
            if (up.enabled && up.contains(mouseX, mouseY)) {
                stack.move(transform, -1);
                return true;
            }
            if (down.enabled && down.contains(mouseX, mouseY)) {
                stack.move(transform, 1);
                return true;
            }
            if (remove.contains(mouseX, mouseY)) {
                stack.remove(transform);
                return true;
            }
            for (Slider slider : sliders) if (slider.press(mouseX, mouseY)) return true;
            return false;
        }
    }
}
