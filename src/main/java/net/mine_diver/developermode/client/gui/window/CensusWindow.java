package net.mine_diver.developermode.client.gui.window;

import net.mine_diver.developermode.client.DeveloperModeClient;
import net.mine_diver.developermode.client.Lang;
import net.mine_diver.developermode.client.census.OreLens;
import net.mine_diver.developermode.client.gui.Button;
import net.mine_diver.developermode.client.gui.Draw;
import net.mine_diver.developermode.client.gui.ItemDraw;
import net.mine_diver.developermode.client.gui.TextField;
import net.mine_diver.developermode.client.gui.Theme;
import net.mine_diver.developermode.client.gui.composer.ComposerScreen;
import net.mine_diver.developermode.client.gui.composer.DevWindow;
import net.mine_diver.developermode.feature.world.Census;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.modificationstation.stationapi.api.block.BlockState;
import net.modificationstation.stationapi.api.registry.BlockRegistry;
import net.modificationstation.stationapi.api.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What the chunks around you are made of, as numbers.
 *
 * <p>Each row is a block state: how many there are to a chunk, the heights it
 * turns up between, and the clumps it comes in. The one picked is drawn at the
 * bottom as a spread over height, beside a reference block counted in the
 * same chunks. That is diamond ore until another row is right clicked, since
 * a new ore is usually judged against the one players already know.
 *
 * <p>A finished census can be kept, and every census after it says how far
 * each block has moved since. That is the loop for tuning a generator:
 * count, change it, regenerate, count again.
 *
 * <p>The lens outlines every block of the kind picked around you in the
 * world, through the walls, for seeing how it really sits in the ground.
 *
 * <p>Counting runs a few milliseconds each frame the window is drawn, so the
 * rows fill in as you watch and the game keeps its frame rate.
 */
public final class CensusWindow extends DevWindow {
    private static final int ROW_HEIGHT = 11;
    private static final int ICON = 9;
    private static final int GAP = 3;
    private static final int SCROLLBAR_WIDTH = 3;
    private static final int CHART_HEIGHT = 26;
    private static final int REFERENCE_FILL = 0x50;
    private static final int PICKED_FILL = 0xB0;
    /** A legend line, the chart, and its axis under it. */
    private static final int DETAIL_HEIGHT = 13 + CHART_HEIGHT + 10;

    private static final int PER_CHUNK_WIDTH = 46;
    private static final int HEIGHTS_WIDTH = 50;
    private static final int VEINS_WIDTH = 40;
    private static final int CHANGE_WIDTH = 46;

    private static final int MIN_RADIUS = 1;
    private static final int MAX_RADIUS = 16;
    /** Enough chunks to average over without waiting long for them. */
    private static final int DEFAULT_RADIUS = 6;
    /** Small enough beside a frame to go unnoticed. */
    private static final long BUDGET_NANOS = 4_000_000;

    private final Button countButton = new Button("gui.developermode.census.count");
    private final Button smallerButton = new Button("gui.developermode.census.smaller");
    private final Button largerButton = new Button("gui.developermode.census.larger");
    private final Button keepButton = new Button("gui.developermode.census.keep");
    private final Button lensButton = new Button("gui.developermode.census.lens");
    private final TextField filter = new TextField(48);

    private final Map<BlockState, Label> labels = new IdentityHashMap<>();
    private final List<Row> rows = new ArrayList<>();

    private int radius = DEFAULT_RADIUS;
    private Census census;
    private Census kept;
    private BlockState picked;
    private BlockState reference = Block.DIAMOND_ORE.getDefaultState();
    private Column sortedBy = Column.PER_CHUNK;
    private boolean ascending;
    private int scrollRow;
    private Row hovered;
    /** The census and how many chunks of it the rows were last built from. */
    private Census builtFrom;
    private int builtAt = -1;
    private boolean builtDone;

    public CensusWindow() {
        super("gui.developermode.census.title", 300, 230);
    }

    public static void open() {
        ComposerScreen.reveal(CensusWindow.class, CensusWindow::new);
    }

    @Override
    public void tick() {
        filter.tick();
    }

    @Override
    protected void renderContent(Minecraft minecraft, int mouseX, int mouseY, float delta, boolean focused) {
        if (census != null && !census.done()) census.step(BUDGET_NANOS);
        refreshRows();

        renderToolbar(minecraft, mouseX, mouseY);
        filter.bounds(contentX(), filterY(), contentWidth());
        filter.render(minecraft, Lang.get("gui.developermode.census.filter"));
        renderHeader(minecraft, mouseX, mouseY);
        renderRows(minecraft, mouseX, mouseY);
        renderDetail(minecraft);
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) {
        if (filter.contains(mouseX, mouseY)) {
            filter.setFocused(true);
            return;
        }
        filter.setFocused(false);

        if (countButton.enabled && countButton.contains(mouseX, mouseY)) {
            count();
        } else if (smallerButton.enabled && smallerButton.contains(mouseX, mouseY)) {
            radius = Math.max(MIN_RADIUS, radius - 1);
        } else if (largerButton.enabled && largerButton.contains(mouseX, mouseY)) {
            radius = Math.min(MAX_RADIUS, radius + 1);
        } else if (lensButton.enabled && lensButton.contains(mouseX, mouseY)) {
            if (OreLens.watched() == picked) OreLens.stop();
            else OreLens.watch(picked);
        } else if (keepButton.enabled && keepButton.contains(mouseX, mouseY)) {
            kept = kept == census ? null : census;
            builtAt = -1;
        } else if (mouseY >= headerY() && mouseY < headerY() + ROW_HEIGHT) {
            sortBy(columnAt(mouseX));
        } else if (hovered != null) {
            if (button == 1) reference = hovered.state;
            else picked = hovered.state;
        }
    }

    @Override
    public void mouseScrolled(int mouseX, int mouseY, int direction) {
        scrollRow -= direction * 3;
        clampScroll();
    }

    @Override
    public void keyPressed(char character, int keyCode) {
        if (filter.keyPressed(character, keyCode)) {
            builtAt = -1;
            scrollRow = 0;
        }
    }

    @Override
    public boolean clearTypingFocus() {
        if (!filter.isFocused()) return false;
        filter.setFocused(false);
        return true;
    }

    private void count() {
        Minecraft minecraft = DeveloperModeClient.minecraft();
        if (minecraft.world == null || minecraft.player == null) return;
        int chunkX = (int) Math.floor(minecraft.player.x) >> 4;
        int chunkZ = (int) Math.floor(minecraft.player.z) >> 4;
        census = Census.around(minecraft.world, chunkX, chunkZ, radius);
    }

    private void renderToolbar(Minecraft minecraft, int mouseX, int mouseY) {
        int y = contentY();
        int x = contentX();

        countButton.bounds(x, y, 40);
        countButton.enabled = census == null || census.done();
        countButton.render(minecraft, mouseX, mouseY);
        x += 40 + GAP;

        smallerButton.bounds(x, y, 12);
        smallerButton.enabled = radius > MIN_RADIUS;
        smallerButton.render(minecraft, mouseX, mouseY);
        x += 12 + GAP;

        int across = radius * 2 + 1;
        String area = Lang.get("gui.developermode.census.area", across, across);
        Draw.text(minecraft, area, x, y + 2, Theme.TEXT_DIM);
        x += Draw.textWidth(minecraft, area) + GAP;

        largerButton.bounds(x, y, 12);
        largerButton.enabled = radius < MAX_RADIUS;
        largerButton.render(minecraft, mouseX, mouseY);

        int keepWidth = 40;
        keepButton.bounds(contentX() + contentWidth() - keepWidth, y, keepWidth);
        keepButton.enabled = census != null && census.done();
        keepButton.toggled = kept != null && kept == census;
        keepButton.key = keepButton.toggled ? "gui.developermode.census.kept" : "gui.developermode.census.keep";
        keepButton.render(minecraft, mouseX, mouseY);

        int lensWidth = 34;
        lensButton.bounds(keepButton.x - GAP - lensWidth, y, lensWidth);
        lensButton.enabled = picked != null || OreLens.watched() != null;
        lensButton.toggled = OreLens.watched() != null && OreLens.watched() == picked;
        lensButton.render(minecraft, mouseX, mouseY);

        String progress = progress();
        int progressX = x + 12 + GAP + 2;
        Draw.text(minecraft, Draw.ellipsize(minecraft, progress, lensButton.x - progressX - GAP),
                progressX, y + 2, Theme.TEXT_FAINT);
    }

    private String progress() {
        if (census == null) return "";
        if (census.counting()) return Lang.get("gui.developermode.census.counting", census.scanned(), census.chunks());
        if (!census.done()) return Lang.get("gui.developermode.census.grouping");
        return census.chunks() == 1
                ? Lang.get("gui.developermode.census.counted.one")
                : Lang.get("gui.developermode.census.counted.many", census.chunks());
    }

    private void renderHeader(Minecraft minecraft, int mouseX, int mouseY) {
        int y = headerY();
        for (Column column : Column.values()) {
            if (column == Column.CHANGE && kept == null) continue;
            int left = columnLeft(column);
            int right = left + columnWidth(column);
            boolean lit = mouseY >= y && mouseY < y + ROW_HEIGHT && mouseX >= left && mouseX < right;
            String label = Lang.get(column.key);
            int color = column == sortedBy ? Theme.ACCENT : lit ? Theme.TEXT : Theme.TEXT_DIM;
            int labelLeft = column == Column.NAME ? left + ICON + 3 : right - Draw.textWidth(minecraft, label) - 2;
            Draw.text(minecraft, label, labelLeft, y + 2, color);
            if (column == sortedBy) {
                sortMark(column == Column.NAME ? labelLeft + Draw.textWidth(minecraft, label) + 2 : labelLeft - 6, y + 2);
            }
        }
        Draw.rect(contentX(), y + ROW_HEIGHT - 1, contentX() + contentWidth(), y + ROW_HEIGHT, Theme.BORDER);
    }

    /** Which way the column it marks runs: up for smallest first, down for largest first. */
    private void sortMark(int left, int top) {
        if (ascending) Draw.triangle(left, top + 6, left + 4, top + 6, left + 2, top + 2, Theme.ACCENT);
        else Draw.triangle(left, top + 2, left + 2, top + 6, left + 4, top + 2, Theme.ACCENT);
    }

    private void renderRows(Minecraft minecraft, int mouseX, int mouseY) {
        int top = listY();
        int visible = visibleRows();
        clampScroll();
        hovered = null;

        if (rows.isEmpty()) {
            String empty = census == null
                    ? Lang.get("gui.developermode.census.empty")
                    : census.chunks() == 0 ? Lang.get("gui.developermode.census.no_chunks") : "";
            Draw.textCentered(minecraft, empty, contentX() + contentWidth() / 2, top + 6, Theme.TEXT_FAINT);
            return;
        }

        for (int i = 0; i < visible && scrollRow + i < rows.size(); i++) {
            Row row = rows.get(scrollRow + i);
            int y = top + i * ROW_HEIGHT;
            boolean over = mouseX >= contentX() && mouseX < contentX() + contentWidth() - SCROLLBAR_WIDTH
                    && mouseY >= y && mouseY < y + ROW_HEIGHT;
            if (over) hovered = row;
            if (row.state == picked) Draw.rect(contentX(), y, contentX() + contentWidth(), y + ROW_HEIGHT, Theme.PANEL_LIT);
            else if (over) Draw.rect(contentX(), y, contentX() + contentWidth(), y + ROW_HEIGHT, Theme.HOVER);
            renderRow(minecraft, row, y);
        }

        renderScrollbar(top, visible);
    }

    private void renderRow(Minecraft minecraft, Row row, int y) {
        Label label = label(row.state);
        int nameLeft = columnLeft(Column.NAME);
        ItemDraw.scaled(minecraft, label.icon, nameLeft, y + 1, ICON);

        int nameColor = row.state == reference ? Theme.LOCKED : row.now == null ? Theme.TEXT_FAINT : Theme.TEXT;
        Draw.text(minecraft, Draw.ellipsize(minecraft, label.name, columnWidth(Column.NAME) - ICON - 5),
                nameLeft + ICON + 3, y + 2, nameColor);

        Census.Tally tally = row.now;
        cell(minecraft, Column.PER_CHUNK, y, tally == null ? "0" : amount(tally.perChunk()), Theme.TEXT);
        cell(minecraft, Column.HEIGHTS, y, tally == null ? "" : tally.lowest() + "-" + tally.highest(), Theme.TEXT_DIM);
        cell(minecraft, Column.VEINS, y, veins(tally), Theme.TEXT_DIM);

        if (kept != null) {
            double change = change(row);
            String text = row.before == null ? Lang.get("gui.developermode.census.new")
                    : Double.isNaN(change) ? "" : percent(change);
            int color = row.before == null || change > 0 ? Theme.NBT_STRING : change < 0 ? Theme.DANGER : Theme.TEXT_FAINT;
            cell(minecraft, Column.CHANGE, y, text, color);
        }
    }

    private void cell(Minecraft minecraft, Column column, int y, String text, int color) {
        int right = columnLeft(column) + columnWidth(column);
        Draw.text(minecraft, text, right - Draw.textWidth(minecraft, text) - 2, y + 2, color);
    }

    @Override
    public void renderOverlay(Minecraft minecraft, int mouseX, int mouseY) {
        if (hovered == null || census == null) return;

        List<String> lines = new ArrayList<>();
        lines.add(label(hovered.state).name);
        Census.Tally tally = hovered.now;
        if (tally == null) {
            lines.add(Lang.get("gui.developermode.census.tooltip.gone"));
        } else {
            lines.add(Lang.get("gui.developermode.census.tooltip.count", amount(tally.perChunk()), tally.mostInAChunk()));
            lines.add(Lang.get("gui.developermode.census.tooltip.share",
                    share(tally.chunksWithAny() / (double) Math.max(1, census.chunks()))));
            lines.add(Lang.get("gui.developermode.census.tooltip.heights", tally.lowest(), tally.highest(), tally.peak()));
            if (tally.hasVeins()) {
                lines.add(Lang.get("gui.developermode.census.tooltip.veins", tally.veins(), amount(tally.meanVein())));
                lines.add(Lang.get("gui.developermode.census.tooltip.vein_sizes", tally.medianVein(), tally.largestVein()));
            } else if (tally.tooCommon()) {
                lines.add(Lang.get("gui.developermode.census.tooltip.too_common"));
            }
        }
        lines.add(Lang.get("gui.developermode.census.tooltip.hint"));

        int width = 0;
        for (String line : lines) width = Math.max(width, Draw.textWidth(minecraft, line));
        int height = lines.size() * 10;
        // To the left of the pointer when the right would run off the
        // screen, and never off either edge.
        int screenWidth = ComposerScreen.instance().width;
        int left = mouseX + 9;
        if (left + width + 3 > screenWidth) left = mouseX - 9 - width;
        left = Math.max(3, Math.min(left, screenWidth - width - 3));
        int top = mouseY - 6;
        Draw.rect(left - 3, top - 3, left + width + 3, top + height + 1, Theme.PANEL_RAISED);
        Draw.outline(left - 3, top - 3, width + 6, height + 4, Theme.BORDER);
        for (int i = 0; i < lines.size(); i++) {
            int color = i == 0 ? Theme.TEXT : i == lines.size() - 1 ? Theme.TEXT_FAINT : Theme.TEXT_DIM;
            Draw.text(minecraft, lines.get(i), left, top + i * 10, color);
        }
    }

    /**
     * The block picked, drawn as a spread over height in front of the
     * reference.
     *
     * <p>Each is scaled to its own peak, since a new ore and diamond can be a
     * hundred times apart in number while the question is whether they sit at
     * the same heights.
     */
    private void renderDetail(Minecraft minecraft) {
        int top = contentY() + contentHeight() - DETAIL_HEIGHT;
        Draw.rect(contentX(), top, contentX() + contentWidth(), top + 1, Theme.BORDER);
        if (census == null) return;

        int x = contentX();
        if (picked == null) {
            String hint = Lang.get("gui.developermode.census.pick");
            Draw.text(minecraft, hint, x, top + 3, Theme.TEXT_FAINT);
            x += Draw.textWidth(minecraft, hint) + 8;
        } else {
            x = legend(minecraft, label(picked).name, x, top + 3, Theme.ACCENT) + 8;
        }
        legend(minecraft, Lang.get("gui.developermode.census.reference", label(reference).name), x, top + 3, Theme.LOCKED);

        int chartTop = top + 13;
        int chartBottom = chartTop + CHART_HEIGHT;
        Draw.rect(contentX(), chartTop, contentX() + contentWidth(), chartBottom, Theme.PANEL_SUNKEN);
        double columnWidth = contentWidth() / (double) census.height();
        chart(census.tally(reference), chartTop, chartBottom, columnWidth, Theme.LOCKED, REFERENCE_FILL);
        if (picked != null) chart(census.tally(picked), chartTop, chartBottom, columnWidth, Theme.ACCENT, PICKED_FILL);

        String bottomLabel = Lang.get("gui.developermode.census.y", census.bottomY());
        String topLabel = Lang.get("gui.developermode.census.y", census.bottomY() + census.height() - 1);
        Draw.text(minecraft, bottomLabel, contentX(), chartBottom + 2, Theme.TEXT_FAINT);
        Draw.text(minecraft, topLabel, contentX() + contentWidth() - Draw.textWidth(minecraft, topLabel), chartBottom + 2,
                Theme.TEXT_FAINT);
    }

    /** @return where the next one can go */
    private static int legend(Minecraft minecraft, String text, int x, int y, int color) {
        Draw.rect(x, y + 1, x + 6, y + 7, color);
        Draw.text(minecraft, text, x + 9, y, color);
        return x + 9 + Draw.textWidth(minecraft, text);
    }

    /**
     * @param fill how opaque the columns are under their solid top edge, low
     *             enough for whatever is drawn behind to show through
     */
    private void chart(Census.Tally tally, int top, int bottom, double columnWidth, int color, int fill) {
        if (tally == null) return;
        int most = 0;
        for (int y = 0; y < census.height(); y++) most = Math.max(most, tally.at(census.bottomY() + y));
        if (most == 0) return;

        for (int y = 0; y < census.height(); y++) {
            int count = tally.at(census.bottomY() + y);
            if (count == 0) continue;
            double left = contentX() + y * columnWidth;
            double right = left + Math.max(1, columnWidth);
            double barTop = bottom - (bottom - top - 1) * count / (double) most;
            Draw.rect(left, barTop, right, bottom, fill << 24 | color & 0xFFFFFF);
            Draw.rect(left, barTop, right, barTop + 1, color);
        }
    }

    private static String veins(Census.Tally tally) {
        if (tally == null || !tally.hasVeins()) return tally != null && tally.tooCommon() ? "-" : "";
        return tally.meanVein() >= 10_000 ? compact((int) tally.meanVein()) : amount(tally.meanVein());
    }

    /** How far the average per chunk has moved since the census kept, as a percentage. */
    private double change(Row row) {
        if (row.before == null) return Double.NaN;
        double before = row.before.perChunk();
        double now = row.now == null ? 0 : row.now.perChunk();
        return before == 0 ? Double.NaN : (now - before) / before * 100;
    }

    private static String amount(double value) {
        if (value >= 100) return String.format(Locale.ROOT, "%.0f", value);
        if (value >= 10) return String.format(Locale.ROOT, "%.1f", value);
        return String.format(Locale.ROOT, "%.2f", value);
    }

    /**
     * A count short enough for a narrow column. Ores come in clumps of a few,
     * but water, sand and bedrock are grouped the same way and lie in sheets
     * tens of thousands of blocks across.
     */
    private static String compact(int value) {
        if (value >= 1_000_000) return String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0);
        if (value >= 10_000) return String.format(Locale.ROOT, "%.1fk", value / 1000.0);
        return Integer.toString(value);
    }

    /** A fraction as a whole percentage, as in "87%". */
    private static String share(double fraction) {
        return String.format(Locale.ROOT, "%.0f%%", fraction * 100);
    }

    /** A change as a signed percentage, as in "+12%". */
    private static String percent(double value) {
        String sign = value > 0 ? "+" : "";
        return sign + String.format(Locale.ROOT, Math.abs(value) >= 10 ? "%.0f%%" : "%.1f%%", value);
    }

    private void renderScrollbar(int top, int visible) {
        if (rows.size() <= visible) return;
        int trackX = contentX() + contentWidth() - SCROLLBAR_WIDTH;
        int trackHeight = visible * ROW_HEIGHT;
        Draw.rect(trackX, top, trackX + SCROLLBAR_WIDTH, top + trackHeight, Theme.PANEL_SUNKEN);
        int thumbHeight = Math.max(8, trackHeight * visible / rows.size());
        int thumbTop = top + (trackHeight - thumbHeight) * scrollRow / (rows.size() - visible);
        Draw.rect(trackX, thumbTop, trackX + SCROLLBAR_WIDTH, thumbTop + thumbHeight, Theme.BORDER_FOCUSED);
    }

    private void sortBy(Column column) {
        if (column == null) return;
        if (column == sortedBy) {
            ascending = !ascending;
        } else {
            sortedBy = column;
            // Names read best from A, and numbers from the most.
            ascending = column == Column.NAME;
        }
        builtAt = -1;
    }

    /**
     * The rows, built again whenever there is something new to show: a few
     * more chunks counted, the census finishing, or the filter or the order
     * changing. Not every frame, since sorting a few hundred rows that have
     * not changed is wasted work.
     */
    private void refreshRows() {
        int at = census == null ? -1 : census.scanned();
        boolean finished = census != null && census.done();
        if (census == builtFrom && at == builtAt && finished == builtDone) return;
        builtFrom = census;
        builtAt = at;
        builtDone = finished;

        rows.clear();
        if (census == null) return;

        String query = filter.text().trim().toLowerCase(Locale.ROOT);
        for (Census.Tally tally : census.tallies()) {
            if (matches(tally.state(), query)) rows.add(new Row(tally.state(), tally, kept == null ? null : kept.tally(tally.state())));
        }
        if (kept != null && kept != census) {
            // Gone since, which is as much a change as anything new.
            for (Census.Tally before : kept.tallies()) {
                if (census.tally(before.state()) == null && matches(before.state(), query))
                    rows.add(new Row(before.state(), null, before));
            }
        }

        Comparator<Row> order = switch (sortedBy) {
            case NAME -> Comparator.comparing(row -> label(row.state).name.toLowerCase(Locale.ROOT));
            case PER_CHUNK -> Comparator.comparingDouble(row -> row.now == null ? 0 : row.now.perChunk());
            case HEIGHTS -> Comparator.comparingInt(row -> row.now == null ? Integer.MIN_VALUE : row.now.peak());
            case VEINS -> Comparator.comparingDouble(row -> row.now == null || !row.now.hasVeins() ? -1 : row.now.meanVein());
            case CHANGE -> Comparator.comparingDouble(row -> {
                double change = change(row);
                return Double.isNaN(change) ? row.before == null ? Double.MAX_VALUE : 0 : change;
            });
        };
        rows.sort(ascending ? order : order.reversed());
    }

    private boolean matches(BlockState state, String query) {
        return query.isEmpty() || label(state).searchKey.contains(query);
    }

    private Label label(BlockState state) {
        return labels.computeIfAbsent(state, Label::of);
    }

    private Column columnAt(int mouseX) {
        for (Column column : Column.values()) {
            if (column == Column.CHANGE && kept == null) continue;
            int left = columnLeft(column);
            if (mouseX >= left && mouseX < left + columnWidth(column)) return column;
        }
        return null;
    }

    /** Numbers line up on the right, and the name takes whatever they leave. */
    private int columnLeft(Column column) {
        int right = contentX() + contentWidth() - SCROLLBAR_WIDTH;
        Column[] columns = Column.values();
        for (int i = columns.length - 1; i > column.ordinal(); i--) {
            if (columns[i] == Column.CHANGE && kept == null) continue;
            right -= columnWidth(columns[i]);
        }
        return column == Column.NAME ? contentX() : right - columnWidth(column);
    }

    private int columnWidth(Column column) {
        return switch (column) {
            case NAME -> columnLeft(Column.PER_CHUNK) - contentX();
            case PER_CHUNK -> PER_CHUNK_WIDTH;
            case HEIGHTS -> HEIGHTS_WIDTH;
            case VEINS -> VEINS_WIDTH;
            case CHANGE -> kept == null ? 0 : CHANGE_WIDTH;
        };
    }

    private int filterY() {
        return contentY() + Button.HEIGHT + GAP;
    }

    private int headerY() {
        return filterY() + TextField.HEIGHT + GAP;
    }

    private int listY() {
        return headerY() + ROW_HEIGHT;
    }

    private int visibleRows() {
        int bottom = contentY() + contentHeight() - DETAIL_HEIGHT - 1;
        return Math.max(1, (bottom - listY()) / ROW_HEIGHT);
    }

    private void clampScroll() {
        scrollRow = Math.max(0, Math.min(scrollRow, Math.max(0, rows.size() - visibleRows())));
    }

    private enum Column {
        NAME("gui.developermode.census.column.block"),
        PER_CHUNK("gui.developermode.census.column.per_chunk"),
        HEIGHTS("gui.developermode.census.column.heights"),
        VEINS("gui.developermode.census.column.veins"),
        CHANGE("gui.developermode.census.column.change");

        final String key;

        Column(String key) {
            this.key = key;
        }
    }

    /** A block state as this census and the one kept count it, either of which can be missing. */
    private record Row(BlockState state, Census.Tally now, Census.Tally before) {}

    /** How a block state is shown and searched for, worked out once per state. */
    private record Label(String name, String searchKey, ItemStack icon) {
        static Label of(BlockState state) {
            Block block = state.getBlock();
            Identifier id = BlockRegistry.INSTANCE.getId(block);
            String name = block.getTranslatedName();
            if (name == null || name.isEmpty() || name.endsWith(".name")) name = id == null ? block.getTranslationKey() : id.path;

            // A state other than the block's own is told apart by its
            // properties, written the way the state writes them.
            if (state != block.getDefaultState()) {
                String written = state.toString();
                int properties = written.indexOf('[');
                if (properties >= 0) name += " " + written.substring(properties);
            }

            Item item = block.asItem();
            ItemStack icon = item == null ? null : new ItemStack(item);
            return new Label(name, (name + " " + id).toLowerCase(Locale.ROOT), icon);
        }
    }
}
