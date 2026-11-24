package com.aureliatransit.architecture.client;

import com.aureliatransit.architecture.station.world.GuidePages;
import com.aureliatransit.architecture.station.world.GuidePages.Click;
import com.aureliatransit.architecture.station.world.GuidePages.Line;
import com.aureliatransit.architecture.station.world.GuidePages.Page;
import com.aureliatransit.architecture.station.world.StationGuide;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.lwjgl.glfw.GLFW;
import org.mtr.aurelia.client.ui.AureliaChrome;
import org.mtr.aurelia.client.ui.AureliaMotion;

import java.util.ArrayList;
import java.util.List;

/**
 * The Station Codex: {@link StationGuide}'s pages as an open book, two pages at a time, with a tab per chapter down the
 * right edge, a search box above, turning block showcases, and lines that run a command, put one in the chat box or turn to a
 * page. It reopens on the spread it was left on until the game closes. Nothing here runs while the screen is shut.
 */
public final class CodexScreen extends Screen {

	private static final int PAGE_WIDTH = 132;
	private static final int PAGE_HEIGHT = 160;
	/** The text column: the width and line count the guide's tests fit every page into. */
	private static final int TEXT_WIDTH = 114;
	private static final int LINES = 14;
	private static final int LINE_HEIGHT = 9;
	private static final int MARGIN = (PAGE_WIDTH - TEXT_WIDTH) / 2;
	private static final int TAB_WIDTH = 22;
	private static final int TAB_HEIGHT = 19;
	private static final int SEARCH_HEIGHT = 14;
	private static final int RESULTS = LINES - 3;

	private static final int COVER = 0xFF5A2E1E;
	private static final int COVER_LIGHT = 0xFF8C3424;
	private static final int COVER_DARK = 0xFF4A130D;
	private static final int GOLD = 0xFFE0B24F;
	private static final int PAPER = 0xFFF7F0DF;
	private static final int PAPER_EDGE = 0xFFE4D8BC;
	private static final int PAGE_STACK = 0xFFD9CCAD;
	private static final int TAB = 0xFFE0D3B4;
	private static final int ARROW = 22;
	private static final long OPEN_MILLIS = 240;
	private static final long TURN_MILLIS = 300;
	private static final long HOVER_MILLIS = 110;
	private static final int INK = 0x2B1D14;
	private static final int FAINT = 0x8A7A66;
	private static final int LINK = 0x1F4FA8;

	/** The left page of the spread the Codex was last left on. */
	private static int lastSpread;

	private final List<Page> pages = StationGuide.PAGES;
	private final List<Hit> hits = new ArrayList<>();
	private int spread;
	private TextFieldWidget search;
	private final long opened = AureliaMotion.now();
	/** The page turn being drawn: when it started and which way (1 forwards, -1 back, 0 none). */
	private long turnStart;
	private int turnDirection;
	private final float[] tabHover = new float[StationGuide.CHAPTERS.size() + 1];
	private long lastFrame = AureliaMotion.now();

	/** Where a line, a tab or a showcased block was drawn this frame, for clicks and tooltips. */
	private record Hit(int x1, int y1, int x2, int y2, Line line, List<Text> tooltip, int page) {

		boolean contains(double x, double y) {
			return x >= x1 && x < x2 && y >= y1 && y < y2;
		}
	}

	private CodexScreen() {
		super(Text.literal(StationGuide.TITLE));
	}

	public static void open() {
		MinecraftClient.getInstance().setScreen(new CodexScreen());
	}

	/** Opens the Codex on the first page of the chapter with {@code title}, or the contents when there is no such chapter. */
	public static void openChapter(String title) {
		final int[] first = GuidePages.firstPages(StationGuide.CHAPTERS);
		for (int i = 0; i < StationGuide.CHAPTERS.size(); i++) {
			if (StationGuide.CHAPTERS.get(i).title().equals(title)) {
				lastSpread = Math.max(0, first[i] - 1) & ~1;
				break;
			}
		}
		open();
	}

	private int left() {
		return (width - 2 * PAGE_WIDTH - TAB_WIDTH) / 2;
	}

	private int top() {
		return (height - PAGE_HEIGHT + SEARCH_HEIGHT + 4) / 2;
	}

	@Override
	protected void init() {
		spread = Math.max(0, Math.min(lastSpread, pages.size() - 1)) & ~1;
		final String query = search == null ? "" : search.getText();
		search = new TextFieldWidget(textRenderer, left() + PAGE_WIDTH + MARGIN + 17, top() - SEARCH_HEIGHT - 3, PAGE_WIDTH - 2 * MARGIN - 22,
				SEARCH_HEIGHT - 2, Text.literal("Search"));
		search.setDrawsBackground(false);
		search.setMaxLength(40);
		search.setText(query);
		search.setPlaceholder(Text.literal("Search the Codex").formatted(Formatting.GRAY));
		addDrawableChild(search);
	}

	private void turn(int by) {
		jump(spread + by);
	}

	/** Shows the spread holding 0-based {@code page}, and closes the search. The page turns over towards it. */
	private void jump(int page) {
		final int next = Math.max(0, Math.min(page, pages.size() - 1)) & ~1;
		if (next != spread) {
			turnDirection = next > spread ? 1 : -1;
			turnStart = AureliaMotion.now();
			if (client != null) {
				client.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(net.minecraft.sound.SoundEvents.ITEM_BOOK_PAGE_TURN, 1));
			}
		}
		spread = next;
		lastSpread = spread;
		if (search != null && !search.getText().isEmpty()) {
			search.setText("");
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context);
		hits.clear();
		final long now = AureliaMotion.now();
		final float frameMillis = Math.min(100, now - lastFrame);
		lastFrame = now;
		final float open = AureliaMotion.easeOut(AureliaMotion.progress(opened, OPEN_MILLIS));
		final boolean live = open >= 1;
		if (!live) {
			// the book rises into place as it opens; nothing under the mouse until it has
			mouseX = -1;
			mouseY = -1;
			context.getMatrices().push();
			final float scale = 0.95F + 0.05F * open;
			context.getMatrices().translate(width / 2F, height / 2F + (1 - open) * 10, 0);
			context.getMatrices().scale(scale, scale, 1);
			context.getMatrices().translate(-width / 2F, -height / 2F, 0);
		}
		final int left = left();
		final int top = top();
		cover(context, left, top);
		tabs(context, left + 2 * PAGE_WIDTH + 4, top + 4, mouseX, mouseY, frameMillis);
		searchPill(context, mouseX, mouseY);

		final String query = search.getText().strip();
		if (query.isEmpty()) {
			page(context, spread, left, top, mouseX, mouseY);
		} else {
			results(context, query, left, top, mouseX, mouseY);
		}
		page(context, spread + 1, left + PAGE_WIDTH, top, mouseX, mouseY);
		arrows(context, left, top, mouseX, mouseY);
		turningSheet(context, left, top);
		super.render(context, mouseX, mouseY, delta);
		if (!live) {
			context.getMatrices().pop();
		}

		for (final Hit hit : hits) {
			if (hit.contains(mouseX, mouseY) && hit.tooltip() != null) {
				context.drawTooltip(textRenderer, hit.tooltip(), mouseX, mouseY);
				break;
			}
		}
	}

	/** Leather cover with gold corners, the two pages with their shade into the spine, and the page edges under them. */
	private void cover(DrawContext context, int left, int top) {
		final int w = 2 * PAGE_WIDTH;
		final int b = 9;
		final int coverH = PAGE_HEIGHT + 2 * b + 2;
		AureliaChrome.shadow(context, left - b, top - b, w + 2 * b, coverH, 8);
		AureliaChrome.rounded(context, left - b, top - b, w + 2 * b, coverH, 8, COVER_DARK);
		AureliaChrome.rounded(context, left - b + 1, top - b + 1, w + 2 * b - 2, coverH - 3, 7, COVER);
		context.fillGradient(left - b + 4, top - b + 1, left + w + b - 4, top + PAGE_HEIGHT / 2, COVER_LIGHT, COVER);
		// stitching around the cover's edge
		final int stitch = AureliaChrome.fade(0xFFD9A77A, 0.55F);
		for (int sx = left - b + 6; sx < left + w + b - 6; sx += 5) {
			context.fill(sx, top - b + 3, sx + 3, top - b + 4, stitch);
			context.fill(sx, top + PAGE_HEIGHT + b - 2, sx + 3, top + PAGE_HEIGHT + b - 1, stitch);
		}
		for (int sy = top - b + 6; sy < top + PAGE_HEIGHT + b - 4; sy += 5) {
			context.fill(left - b + 3, sy, left - b + 4, sy + 3, stitch);
		}
		for (final int cx : new int[]{left - b, left + w + b - 9}) {
			for (final int cy : new int[]{top - b, top + PAGE_HEIGHT + b + 2 - 9}) {
				AureliaChrome.rounded(context, cx, cy, 9, 9, 3, GOLD);
				AureliaChrome.rounded(context, cx + 2, cy + 2, 5, 5, 2, 0xFFB8862E);
			}
		}
		// the block of pages under the open spread
		context.fill(left + 2, top + PAGE_HEIGHT, left + w - 2, top + PAGE_HEIGHT + 3, PAGE_STACK);
		context.fill(left + 4, top + PAGE_HEIGHT + 1, left + w / 2 - 6, top + PAGE_HEIGHT + 2, PAPER_EDGE);
		context.fill(left + w / 2 + 6, top + PAGE_HEIGHT + 1, left + w - 4, top + PAGE_HEIGHT + 2, PAPER_EDGE);
		AureliaChrome.rounded(context, left, top, w, PAGE_HEIGHT, 3, PAPER);
		// shade towards the spine, darkest in the fold
		for (int i = 0; i < 10; i++) {
			final int shade = AureliaChrome.fade(0xFF6B5A3A, 0.22F * (1 - i / 10F) * (1 - i / 10F));
			context.fill(left + PAGE_WIDTH - 1 - i, top, left + PAGE_WIDTH - i, top + PAGE_HEIGHT, shade);
			context.fill(left + PAGE_WIDTH + i, top, left + PAGE_WIDTH + i + 1, top + PAGE_HEIGHT, shade);
		}
		context.fill(left + PAGE_WIDTH - 1, top, left + PAGE_WIDTH + 1, top + PAGE_HEIGHT, AureliaChrome.fade(0xFF6B5A3A, 0.45F));
	}

	/** The search field as a rounded pill above the right page, with a lens drawn in front of it. */
	private void searchPill(DrawContext context, int mouseX, int mouseY) {
		final int x = left() + PAGE_WIDTH + MARGIN;
		final int y = top() - SEARCH_HEIGHT - 7;
		final int w = PAGE_WIDTH - 2 * MARGIN;
		final boolean hot = search.isFocused() || mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + SEARCH_HEIGHT + 2;
		AureliaChrome.rounded(context, x, y, w, SEARCH_HEIGHT + 2, 8, hot ? 0xFF2A1A14 : 0xCC1E120D);
		AureliaChrome.roundedFrame(context, x, y, w, SEARCH_HEIGHT + 2, 8, search.isFocused() ? GOLD : AureliaChrome.fade(GOLD, 0.35F));
		// the lens: a small ring and its handle
		final int lx = x + 7;
		final int ly = y + 5;
		AureliaChrome.roundedFrame(context, lx - 2, ly - 2, 6, 6, 3, 0xFFD8C9A8);
		context.fill(lx + 3, ly + 3, lx + 5, ly + 5, 0xFFD8C9A8);
	}

	/** Round previous and next buttons in the pages' bottom corners; each shows only when there is a page that way. */
	private void arrows(DrawContext context, int left, int top, int mouseX, int mouseY) {
		final int y = top + PAGE_HEIGHT - ARROW - 3;
		arrow(context, left + 5, y, false, spread > 0, mouseX, mouseY);
		arrow(context, left + 2 * PAGE_WIDTH - 5 - ARROW, y, true, spread + 2 < pages.size(), mouseX, mouseY);
	}

	private void arrow(DrawContext context, int x, int y, boolean forward, boolean shown, int mouseX, int mouseY) {
		if (!shown) {
			return;
		}
		final boolean hovered = mouseX >= x && mouseX < x + ARROW && mouseY >= y && mouseY < y + ARROW;
		if (hovered) {
			AureliaChrome.rounded(context, x, y, ARROW, ARROW, ARROW / 2, 0x22000000 | (COVER & 0xFFFFFF));
		}
		final int color = hovered ? COVER : 0xFF9A8A70;
		final int cx = x + ARROW / 2;
		final int cy = y + ARROW / 2;
		// a chevron, four pixels each side of its point
		for (int i = 0; i <= 4; i++) {
			final int px = forward ? cx - 2 + i : cx + 2 - i;
			context.fill(px, cy - 4 + i, px + 2, cy - 4 + i + 1, color);
			context.fill(px, cy + 4 - i, px + 2, cy + 4 - i + 1, color);
		}
		hits.add(new Hit(x, y, x + ARROW, y + ARROW, null, null, forward ? spread + 2 : Math.max(0, spread - 2)));
	}

	/**
	 * A page turning over the spread: the sheet lifts off one page, narrows towards the spine and lands on the other,
	 * shaded darker while it stands upright. The new pages are already drawn underneath.
	 */
	private void turningSheet(DrawContext context, int left, int top) {
		if (turnDirection == 0) {
			return;
		}
		final float t = AureliaMotion.easeInOut(AureliaMotion.progress(turnStart, TURN_MILLIS));
		if (t >= 1) {
			turnDirection = 0;
			return;
		}
		final int spine = left + PAGE_WIDTH;
		final boolean firstHalf = t < 0.5F;
		final float span = firstHalf ? 1 - t * 2 : (t - 0.5F) * 2;
		final int w = Math.max(1, Math.round(PAGE_WIDTH * span));
		// forwards: the right page lifts and lands on the left
		final boolean onRight = (turnDirection > 0) == firstHalf;
		final int x = onRight ? spine : spine - w;
		final int lift = Math.round((1 - span) * 6);
		context.getMatrices().push();
		context.getMatrices().translate(0, 0, 250);
		context.fill(x, top - lift, x + w, top + PAGE_HEIGHT + lift, AureliaChrome.mix(PAPER, 0xFFB8A888, (1 - span) * 0.8F));
		final int edge = onRight ? x + w - 1 : x;
		context.fill(edge, top - lift, edge + 1, top + PAGE_HEIGHT + lift, PAPER_EDGE);
		// its shadow falls on the page beneath
		final int shadow = AureliaChrome.fade(0xFF000000, 0.18F * (1 - span));
		if (onRight) {
			context.fill(x + w, top, Math.min(spine + PAGE_WIDTH, x + w + 6), top + PAGE_HEIGHT, shadow);
		} else {
			context.fill(Math.max(left, x - 6), top, x, top + PAGE_HEIGHT, shadow);
		}
		context.getMatrices().pop();
	}

	/** The contents tab, then one tab per chapter with its icon; the open chapter's tab joins the page, a hovered one slides out. */
	private void tabs(DrawContext context, int x, int y, int mouseX, int mouseY, float frameMillis) {
		final int open = GuidePages.chapterAt(StationGuide.CHAPTERS, spread);
		final int[] first = GuidePages.firstPages(StationGuide.CHAPTERS);
		for (int i = -1; i < StationGuide.CHAPTERS.size(); i++) {
			final int ty = y + (i + 1) * (TAB_HEIGHT + 2);
			final boolean here = i == open;
			final boolean hovered = mouseX >= x && mouseX < x + TAB_WIDTH + 4 && mouseY >= ty && mouseY < ty + TAB_HEIGHT;
			tabHover[i + 1] = Math.max(0, Math.min(1, tabHover[i + 1] + (hovered ? 1 : -1) * frameMillis / HOVER_MILLIS));
			final int out = here ? 4 : Math.round(AureliaMotion.easeOut(tabHover[i + 1]) * 3);
			AureliaChrome.rounded(context, x - 6, ty, TAB_WIDTH + 6 + out, TAB_HEIGHT, 4, here ? COVER_DARK : AureliaChrome.mix(COVER_DARK, COVER, 0.5F));
			AureliaChrome.rounded(context, x - 6, ty + 1, TAB_WIDTH + 5 + out, TAB_HEIGHT - 2, 3, here ? PAPER : AureliaChrome.mix(TAB, PAPER, tabHover[i + 1]));
			if (here) {
				context.fill(x + TAB_WIDTH + out - 2, ty + 3, x + TAB_WIDTH + out - 1, ty + TAB_HEIGHT - 3, GOLD);
			}
			final String icon = i < 0 ? "minecraft:book" : StationGuide.CHAPTERS.get(i).icon();
			context.drawItem(new ItemStack(Registries.ITEM.get(new Identifier(icon))), x + 1 + out, ty + 2);
			final String title = i < 0 ? "Contents" : StationGuide.CHAPTERS.get(i).title();
			hits.add(new Hit(x, ty, x + TAB_WIDTH + 4, ty + TAB_HEIGHT, null, List.of(Text.literal(title)), i < 0 ? 0 : first[i] - 1));
		}
	}

	private void page(DrawContext context, int index, int x, int top, int mouseX, int mouseY) {
		if (index >= pages.size()) {
			return;
		}
		final Page page = pages.get(index);
		draw(context, page, x, top, mouseX, mouseY);
		final String number = Integer.toString(index + 1);
		context.drawText(textRenderer, number, x + (PAGE_WIDTH - textRenderer.getWidth(number)) / 2, top + PAGE_HEIGHT - 13, FAINT, false);
		if (!page.blocks().isEmpty()) {
			showcase(context, page.blocks(), x + MARGIN, top + MARGIN + (LINES - GuidePages.SHOWCASE_LINES) * LINE_HEIGHT, mouseX, mouseY);
		}
	}

	/** The search results as a page of links, best first. */
	private void results(DrawContext context, String query, int x, int top, int mouseX, int mouseY) {
		final List<Line> lines = new ArrayList<>();
		final List<Integer> found = GuidePages.search(pages, query, I18n::translate, RESULTS);
		lines.add(Line.text(found.isEmpty() ? "Nothing found." : "Click one to turn to it."));
		for (final int i : found) {
			final Page page = pages.get(i);
			lines.add(new Line((i + 1) + "  " + page.heading(), false, Click.PAGE, Integer.toString(i + 1), "Page " + (i + 1)));
		}
		draw(context, new Page("Search", lines), x, top, mouseX, mouseY);
	}

	/** A page's heading and lines, wrapped to the text column; clickable lines are blue, underlined under the mouse. */
	private void draw(DrawContext context, Page page, int x, int top, int mouseX, int mouseY) {
		final int textX = x + MARGIN;
		int y = top + MARGIN;
		for (final OrderedText row : textRenderer.wrapLines(Text.literal(page.heading()).formatted(Formatting.BOLD), TEXT_WIDTH)) {
			context.drawText(textRenderer, row, textX, y, INK, false);
			y += LINE_HEIGHT;
		}
		context.fill(textX, y + 2, textX + 20, y + 3, COVER_LIGHT);
		y += LINE_HEIGHT;
		for (final Line line : page.lines()) {
			final String shown = line.key() ? I18n.translate(line.text()) : line.text();
			final List<OrderedText> rows = new ArrayList<>();
			final boolean link = line.click() != Click.NONE;
			int height = 0;
			for (final String paragraph : shown.split("\n", -1)) {
				height += paragraph.isEmpty() ? 1 : textRenderer.wrapLines(Text.literal(paragraph), TEXT_WIDTH).size();
			}
			final boolean hovered = link && mouseX >= textX && mouseX < textX + TEXT_WIDTH && mouseY >= y && mouseY < y + height * LINE_HEIGHT;
			final Style style = link ? Style.EMPTY.withUnderline(hovered) : Style.EMPTY;
			for (final String paragraph : shown.split("\n", -1)) {
				if (paragraph.isEmpty()) {
					rows.add(OrderedText.EMPTY);
				} else {
					rows.addAll(textRenderer.wrapLines(Text.literal(paragraph).setStyle(style), TEXT_WIDTH));
				}
			}
			if (link) {
				hits.add(new Hit(textX, y, textX + TEXT_WIDTH, y + rows.size() * LINE_HEIGHT, line, tooltip(line), -1));
			}
			for (final OrderedText row : rows) {
				context.drawText(textRenderer, row, textX, y, link ? LINK : INK, false);
				y += LINE_HEIGHT;
			}
		}
	}

	private static List<Text> tooltip(Line line) {
		final List<Text> tooltip = new ArrayList<>();
		if (line.hover() != null) {
			tooltip.add(Text.literal(line.hover()));
		}
		tooltip.add(Text.literal(switch (line.click()) {
			case RUN -> "Click to run";
			case CHAT -> "Click to put in chat";
			default -> "Click to turn there";
		}).formatted(Formatting.GRAY));
		return tooltip;
	}

	/** Up to three blocks turning slowly side by side, each named on hover. */
	private void showcase(DrawContext context, List<String> blocks, int x, int y, int mouseX, int mouseY) {
		final int count = Math.min(3, blocks.size());
		final int cell = TEXT_WIDTH / count;
		final float angle = (System.currentTimeMillis() % 9000L) / 9000f * 360f;
		for (int i = 0; i < count; i++) {
			final BlockState state = Registries.BLOCK.get(new Identifier(blocks.get(i))).getDefaultState();
			final int cx = x + i * cell + cell / 2;
			final int cy = y + GuidePages.SHOWCASE_LINES * LINE_HEIGHT / 2;
			block(context, state, cx, cy, angle + i * 40f);
			hits.add(new Hit(cx - cell / 2, y, cx + cell / 2, y + GuidePages.SHOWCASE_LINES * LINE_HEIGHT, null,
					List.of(state.getBlock().getName()), -1));
		}
	}

	/** One block's model, about 24 px across, tilted towards the reader and turned by {@code angle}. */
	private void block(DrawContext context, BlockState state, int cx, int cy, float angle) {
		if (state.getRenderType() != BlockRenderType.MODEL) {
			final MatrixStack matrices = context.getMatrices();
			matrices.push();
			matrices.translate(cx - 16, cy - 16, 0);
			matrices.scale(2, 2, 1);
			context.drawItem(new ItemStack(state.getBlock()), 0, 0);
			matrices.pop();
			return;
		}
		final MatrixStack matrices = context.getMatrices();
		matrices.push();
		matrices.translate(cx, cy, 150);
		matrices.scale(20, -20, 20);
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(25));
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(angle));
		matrices.translate(-0.5, -0.5, -0.5);
		DiffuseLighting.enableGuiDepthLighting();
		client.getBlockRenderManager().renderBlockAsEntity(state, matrices, context.getVertexConsumers(),
				LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);
		context.draw();
		DiffuseLighting.disableGuiDepthLighting();
		matrices.pop();
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (AureliaMotion.progress(opened, OPEN_MILLIS) < 1) {
			return false;
		}
		if (super.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			return false;
		}
		for (final Hit hit : hits) {
			if (!hit.contains(mouseX, mouseY)) {
				continue;
			}
			if (hit.line() == null) {
				if (hit.page() < 0) {
					continue;
				}
				jump(hit.page());
				return true;
			}
			return act(hit.line());
		}
		return false;
	}

	private boolean act(Line line) {
		switch (line.click()) {
			case RUN -> {
				close();
				if (client.player != null) {
					client.player.networkHandler.sendChatCommand(line.value().substring(1));
				}
			}
			case CHAT -> client.setScreen(new ChatScreen(line.value()));
			case PAGE -> jump(Integer.parseInt(line.value()) - 1);
			default -> {
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (!search.isFocused()) {
			if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_PAGE_UP) {
				turn(-2);
				return true;
			}
			if (keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
				turn(2);
				return true;
			}
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		turn(amount > 0 ? -2 : 2);
		return true;
	}

	@Override
	public void removed() {
		lastSpread = spread;
	}
}
