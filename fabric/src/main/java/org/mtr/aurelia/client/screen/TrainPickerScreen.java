package org.mtr.aurelia.client.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.mtr.aurelia.client.ui.AureliaChrome;
import org.mtr.aurelia.client.ui.AureliaMotion;
import org.mtr.aurelia.client.ui.AureliaPalette;
import org.mtr.core.data.Siding;
import org.mtr.core.data.VehicleCar;
import org.mtr.core.operation.UpdateDataRequest;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mod.InitClient;
import org.mtr.mod.client.CustomResourceLoader;
import org.mtr.mod.client.MinecraftClientData;
import org.mtr.mod.packet.PacketUpdateData;
import org.mtr.mod.resource.VehicleResource;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;

/**
 * ATR's train picker for a siding: the trains on the left, the siding's train car by car on the right, and a 3D preview
 * on a piece of track at the bottom. Hovering a train in the list shows that car; otherwise the whole train is shown.
 * Saves the same siding data as MTR's selector, which comes back when the "Train picker" Look switch is off.
 */
public final class TrainPickerScreen extends Screen {

	private static final int MARGIN = 12;
	private static final int ROW_HEIGHT = 24;
	private static final int HEADER = 30;
	private static final int BUTTON_HEIGHT = 16;
	private static final long OPEN_MILLIS = 240;
	private static final int STAGE_TOP = 0xFF20262E;
	private static final int STAGE_BOTTOM = 0xFF101317;

	private final Siding siding;
	@Nullable
	private final Screen previous;
	private final List<VehicleResource> allTrains = new ObjectArrayList<>();
	private final List<VehicleResource> shownTrains = new ObjectArrayList<>();
	private final long openedAt = AureliaMotion.now();

	private TextFieldWidget search;
	private double listScroll;
	private float dragYaw;
	private boolean dragging;
	@Nullable
	private VehicleResource hoveredTrain;
	private int hoveredCar = -1;

	public TrainPickerScreen(Siding siding, @Nullable Screen previous) {
		super(Text.translatable("gui.aurelia.train_picker.title"));
		this.siding = siding;
		this.previous = previous;
		CustomResourceLoader.iterateVehicles(siding.getTransportMode(), allTrains::add);
	}

	@Override
	protected void init() {
		final String oldSearch = search == null ? "" : search.getText();
		search = new TextFieldWidget(textRenderer, MARGIN + 18, HEADER + 6, listWidth() - 24, 12, Text.translatable("gui.aurelia.train_picker.search"));
		search.setDrawsBackground(false);
		search.setPlaceholder(Text.translatable("gui.aurelia.train_picker.search").formatted(Formatting.GRAY));
		search.setText(oldSearch);
		search.setChangedListener(text -> {
			filter();
			listScroll = 0;
		});
		addDrawableChild(search);
		filter();
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context);
		final float open = AureliaMotion.easeOut(AureliaMotion.progress(openedAt, OPEN_MILLIS));
		context.getMatrices().push();
		context.getMatrices().translate(0, (1 - open) * 8, 0);

		context.drawText(textRenderer, title.copy().formatted(Formatting.BOLD), MARGIN, 12, AureliaPalette.FG, false);
		final String sidingName = siding.getName();
		if (!sidingName.isEmpty()) {
			final int titleWidth = textRenderer.getWidth(title.copy().formatted(Formatting.BOLD));
			context.drawText(textRenderer, textRenderer.trimToWidth(sidingName.replace('|', ' '), Math.max(0, width - MARGIN * 3 - titleWidth - 70)), MARGIN + titleWidth + 8, 12, AureliaPalette.TEXT_DIM, false);
		}
		button(context, doneX(), 8, 60, Text.translatable("gui.aurelia.train_picker.done"), mouseX, mouseY, true);

		renderList(context, mouseX, mouseY);
		renderFormation(context, mouseX, mouseY);
		renderStage(context, mouseX, mouseY);
		context.getMatrices().pop();

		super.render(context, mouseX, mouseY, delta);
	}

	private void renderList(DrawContext context, int mouseX, int mouseY) {
		final int x = MARGIN;
		final int w = listWidth();
		AureliaChrome.rounded(context, x, HEADER, w, 22, 6, AureliaPalette.RAISED);
		AureliaChrome.roundedFrame(context, x, HEADER, w, 22, 6, search.isFocused() ? AureliaPalette.ACCENT_LIGHT : AureliaPalette.LINE);
		magnifier(context, x + 8, HEADER + 7);

		final int top = listTop();
		final int bottom = height - MARGIN;
		hoveredTrain = null;
		if (shownTrains.isEmpty()) {
			context.drawCenteredTextWithShadow(textRenderer, Text.translatable("gui.aurelia.train_picker.nothing"), x + w / 2, top + 20, AureliaPalette.TEXT_DIM);
			return;
		}
		listScroll = Math.max(0, Math.min(listScroll, maxScroll()));
		final double remaining = remainingLength();
		context.enableScissor(x, top, x + w, bottom);
		for (int i = 0; i < shownTrains.size(); i++) {
			final int rowY = top + i * ROW_HEIGHT - (int) listScroll;
			if (rowY + ROW_HEIGHT < top || rowY > bottom) {
				continue;
			}
			final VehicleResource train = shownTrains.get(i);
			final boolean fits = fits(train, remaining);
			final boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= Math.max(top, rowY) && mouseY < Math.min(bottom, rowY + ROW_HEIGHT - 2);
			if (hovered) {
				hoveredTrain = train;
			}
			AureliaChrome.rounded(context, x, rowY, w, ROW_HEIGHT - 2, 5, hovered && fits ? AureliaPalette.RAISED_HOVER : AureliaPalette.RAISED);
			context.fill(x + 4, rowY + 5, x + 7, rowY + ROW_HEIGHT - 7, 0xFF000000 | train.getColor());
			final String length = fits ? Text.translatable("gui.aurelia.train_picker.length", metres(train.getLength())).getString() : Text.translatable("gui.aurelia.train_picker.too_long").getString();
			final int lengthWidth = textRenderer.getWidth(length);
			context.drawText(textRenderer, textRenderer.trimToWidth(train.getName().getString(), w - 24 - lengthWidth), x + 12, rowY + 7, fits ? AureliaPalette.FG : AureliaPalette.TEXT_DIM, false);
			context.drawText(textRenderer, length, x + w - 8 - lengthWidth, rowY + 7, fits ? AureliaPalette.TEXT_DIM : AureliaPalette.WARN, false);
			if (hovered && fits) {
				plus(context, x + w - 16 - lengthWidth, rowY + 11);
			}
		}
		context.disableScissor();
		if (maxScroll() > 0) {
			final int trackHeight = bottom - top;
			final int thumb = Math.max(16, trackHeight * trackHeight / (shownTrains.size() * ROW_HEIGHT));
			final int thumbY = top + (int) ((trackHeight - thumb) * listScroll / maxScroll());
			AureliaChrome.rounded(context, x + w + 3, thumbY, 3, thumb, 1, AureliaPalette.LINE);
		}
	}

	private void renderFormation(DrawContext context, int mouseX, int mouseY) {
		final int x = rightX();
		final int w = rightWidth();
		final List<VehicleCar> cars = siding.getVehicleCars();
		final VehicleResource info = hoveredTrain != null ? hoveredTrain : hoveredCar >= 0 && hoveredCar < cars.size() ? resource(cars.get(hoveredCar).getVehicleId()) : null;

		int y = HEADER;
		if (info != null) {
			context.drawText(textRenderer, textRenderer.trimToWidth(info.getName().getString(), w), x, y, AureliaPalette.FG, false);
			y += 11;
			final String description = info.getDescription().getString().replace('|', ' ').replace('\n', ' ').trim();
			final String details = Text.translatable("gui.aurelia.train_picker.length", metres(info.getLength())).getString() + (description.isEmpty() ? "" : "  ·  " + description);
			context.drawText(textRenderer, textRenderer.trimToWidth(details, w), x, y, AureliaPalette.TEXT_DIM, false);
		} else {
			final String count = cars.size() == 1 ? Text.translatable("gui.aurelia.train_picker.one_car").getString() : Text.translatable("gui.aurelia.train_picker.cars", cars.size()).getString();
			context.drawText(textRenderer, Text.translatable("gui.aurelia.train_picker.formation").formatted(Formatting.BOLD), x, y, AureliaPalette.FG, false);
			context.drawText(textRenderer, count, x, y + 11, AureliaPalette.TEXT_DIM, false);
		}
		button(context, x + w - 50, HEADER, 50, Text.translatable("gui.aurelia.train_picker.clear"), mouseX, mouseY, !cars.isEmpty());
		button(context, x + w - 106, HEADER, 52, Text.translatable("gui.aurelia.train_picker.repeat"), mouseX, mouseY, !cars.isEmpty() && remainingLength() > 0);

		// the train, car by car, as wide as the siding is long
		final int stripY = stripY();
		AureliaChrome.rounded(context, x, stripY, w, 18, 5, AureliaPalette.SURFACE);
		AureliaChrome.roundedFrame(context, x, stripY, w, 18, 5, AureliaPalette.LINE);
		hoveredCar = -1;
		final double railLength = Math.max(1, siding.getRailLength());
		if (cars.isEmpty()) {
			context.drawText(textRenderer, textRenderer.trimToWidth(Text.translatable("gui.aurelia.train_picker.empty").getString(), w - 12), x + 6, stripY + 5, AureliaPalette.TEXT_DIM, false);
		} else {
			double used = 0;
			for (int i = 0; i < cars.size(); i++) {
				final VehicleCar car = cars.get(i);
				final int carX = x + 2 + (int) Math.round(used / railLength * (w - 4));
				used += car.getTotalLength(i == 0, i == cars.size() - 1);
				final int carEnd = x + 2 + (int) Math.round(Math.min(railLength, used) / railLength * (w - 4));
				final int carWidth = Math.max(3, carEnd - carX - 1);
				final boolean hovered = mouseX >= carX && mouseX < carX + carWidth && mouseY >= stripY && mouseY < stripY + 18;
				if (hovered) {
					hoveredCar = i;
				}
				final VehicleResource resource = resource(car.getVehicleId());
				final int color = resource == null ? AureliaPalette.ACCENT : 0xFF000000 | resource.getColor();
				AureliaChrome.rounded(context, carX, stripY + 3, carWidth, 12, 3, hovered ? AureliaChrome.mix(color, AureliaPalette.WARN, 0.55F) : color);
				context.fill(carX + 2, stripY + 6, carX + carWidth - 2, stripY + 8, AureliaChrome.fade(0xFFFFFFFF, 0.35F));
			}
		}
		final double used = railLength - remainingLength();
		final String fit = Text.translatable("gui.aurelia.train_picker.fit", metres(used), metres(railLength)).getString();
		context.drawText(textRenderer, fit, x, stripY + 22, AureliaPalette.TEXT_DIM, false);
		if (hoveredCar >= 0) {
			final String hint = Text.translatable("gui.aurelia.train_picker.remove_hint").getString();
			context.drawText(textRenderer, hint, x + w - textRenderer.getWidth(hint), stripY + 22, AureliaPalette.WARN, false);
		}
	}

	private void renderStage(DrawContext context, int mouseX, int mouseY) {
		final int x = rightX();
		final int y = stageY();
		final int w = rightWidth();
		final int h = height - MARGIN - y;
		if (h < 40) {
			return;
		}
		AureliaChrome.rounded(context, x, y, w, h, 8, STAGE_BOTTOM);
		context.fillGradient(x + 1, y + 1, x + w - 1, y + h * 2 / 3, STAGE_TOP, STAGE_BOTTOM);
		AureliaChrome.roundedFrame(context, x, y, w, h, 8, AureliaPalette.LINE);

		final List<VehicleResource> shown = new ObjectArrayList<>();
		final List<VehicleCar> cars = siding.getVehicleCars();
		if (hoveredTrain != null) {
			shown.add(hoveredTrain);
		} else if (hoveredCar >= 0 && hoveredCar < cars.size()) {
			final VehicleResource resource = resource(cars.get(hoveredCar).getVehicleId());
			if (resource != null) {
				shown.add(resource);
			}
		} else {
			for (final VehicleCar car : cars) {
				final VehicleResource resource = resource(car.getVehicleId());
				if (resource != null) {
					shown.add(resource);
				}
			}
		}
		if (shown.isEmpty()) {
			context.drawCenteredTextWithShadow(textRenderer, Text.translatable("gui.aurelia.train_picker.empty"), x + w / 2, y + h / 2 - 4, AureliaPalette.TEXT_DIM);
			return;
		}

		final float sway = (float) Math.sin((AureliaMotion.now() - openedAt) / 2600.0) * 9;
		final float yaw = 68 + (dragging ? 0 : sway) + dragYaw;
		final TrainPreview.State state = TrainPreview.draw(context, shown, x + 1, y + 1, w - 2, h - 2, yaw, 14);
		if (state == TrainPreview.State.LOADING) {
			context.drawCenteredTextWithShadow(textRenderer, Text.translatable("gui.aurelia.train_picker.loading"), x + w / 2, y + h / 2 - 4, AureliaPalette.TEXT_DIM);
		} else if (state == TrainPreview.State.UNSUPPORTED) {
			context.drawCenteredTextWithShadow(textRenderer, Text.translatable("gui.aurelia.train_picker.no_preview"), x + w / 2, y + h / 2 - 4, AureliaPalette.TEXT_DIM);
		} else if (inStage(mouseX, mouseY)) {
			final String drag = Text.translatable("gui.aurelia.train_picker.drag").getString();
			context.drawText(textRenderer, drag, x + w - 8 - textRenderer.getWidth(drag), y + h - 14, AureliaChrome.fade(AureliaPalette.TEXT_DIM, 0.8F), false);
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (AureliaMotion.progress(openedAt, OPEN_MILLIS) < 1) {
			return true;
		}
		if (button == 0) {
			if (hit(mouseX, mouseY, doneX(), 8, 60, BUTTON_HEIGHT)) {
				click();
				close();
				return true;
			}
			final List<VehicleCar> cars = siding.getVehicleCars();
			if (!cars.isEmpty() && hit(mouseX, mouseY, rightX() + rightWidth() - 50, HEADER, 50, BUTTON_HEIGHT)) {
				click();
				setCars(new ObjectArrayList<>());
				return true;
			}
			if (!cars.isEmpty() && hit(mouseX, mouseY, rightX() + rightWidth() - 106, HEADER, 52, BUTTON_HEIGHT)) {
				click();
				repeat();
				return true;
			}
			if (hoveredCar >= 0 && hoveredCar < cars.size()) {
				click();
				final ObjectArrayList<VehicleCar> newCars = new ObjectArrayList<>(cars);
				newCars.remove(hoveredCar);
				setCars(newCars);
				hoveredCar = -1;
				return true;
			}
			if (hoveredTrain != null && fits(hoveredTrain, remainingLength())) {
				click();
				final ObjectArrayList<VehicleCar> newCars = new ObjectArrayList<>(cars);
				newCars.add(car(hoveredTrain));
				setCars(newCars);
				return true;
			}
			if (inStage(mouseX, mouseY)) {
				dragging = true;
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (dragging) {
			dragYaw = (float) ((dragYaw + deltaX * 0.9) % 360);
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		dragging = false;
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (mouseX < MARGIN + listWidth() + 6) {
			listScroll = Math.max(0, Math.min(maxScroll(), listScroll - amount * ROW_HEIGHT * 1.5));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, amount);
	}

	@Override
	public void close() {
		if (client != null && previous != null) {
			client.setScreen(previous);
		} else {
			super.close();
		}
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	private void filter() {
		final String query = search == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
		shownTrains.clear();
		for (final VehicleResource train : allTrains) {
			if (query.isEmpty() || train.getName().getString().toLowerCase(Locale.ROOT).contains(query) || train.getId().toLowerCase(Locale.ROOT).contains(query)) {
				shownTrains.add(train);
			}
		}
	}

	/** Adds the train's cars again and again until the siding is full, as MTR's "duplicate" button does. */
	private void repeat() {
		final ObjectArrayList<VehicleCar> pattern = new ObjectArrayList<>(siding.getVehicleCars());
		while (true) {
			final ObjectArrayList<VehicleCar> current = new ObjectArrayList<>(siding.getVehicleCars());
			final int before = current.size();
			current.addAll(pattern);
			siding.setVehicleCars(current);
			if (siding.getVehicleCars().size() == before) {
				break;
			}
		}
		send();
	}

	private void setCars(ObjectArrayList<VehicleCar> cars) {
		siding.setVehicleCars(cars);
		send();
	}

	private void send() {
		InitClient.REGISTRY_CLIENT.sendPacketToServer(new PacketUpdateData(new UpdateDataRequest(MinecraftClientData.getDashboardInstance()).addSiding(siding)));
	}

	private double remainingLength() {
		final List<VehicleCar> cars = siding.getVehicleCars();
		double remaining = siding.getRailLength();
		for (int i = 0; i < cars.size(); i++) {
			remaining -= cars.get(i).getTotalLength(i == 0, false);
		}
		return remaining;
	}

	/** The same test MTR's selector uses to grey out a train. */
	private static boolean fits(VehicleResource train, double remaining) {
		return train.getCouplingPadding1() + train.getLength() <= remaining;
	}

	private static VehicleCar car(VehicleResource train) {
		return new VehicleCar(train.getId(), train.getLength(), train.getWidth(), train.getBogie1Position(), train.getBogie2Position(), train.getCouplingPadding1(), train.getCouplingPadding2());
	}

	@Nullable
	private VehicleResource resource(String id) {
		for (final VehicleResource train : allTrains) {
			if (train.getId().equals(id)) {
				return train;
			}
		}
		return null;
	}

	private void button(DrawContext context, int x, int y, int w, Text text, int mouseX, int mouseY, boolean active) {
		final boolean hovered = active && hit(mouseX, mouseY, x, y, w, BUTTON_HEIGHT);
		AureliaChrome.button(context, x, y, w, BUTTON_HEIGHT, hovered, active, false);
		context.drawCenteredTextWithShadow(textRenderer, text, x + w / 2, y + 4, active ? AureliaPalette.FG : AureliaPalette.TEXT_DIM);
	}

	private void click() {
		MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1));
	}

	private static void magnifier(DrawContext context, int x, int y) {
		AureliaChrome.roundedFrame(context, x, y, 7, 7, 3, AureliaPalette.TEXT_DIM);
		context.fill(x + 6, y + 6, x + 8, y + 8, AureliaPalette.TEXT_DIM);
		context.fill(x + 7, y + 7, x + 9, y + 9, AureliaPalette.TEXT_DIM);
	}

	private static void plus(DrawContext context, int cx, int cy) {
		context.fill(cx - 3, cy - 1, cx + 4, cy, AureliaPalette.ACCENT_LIGHT);
		context.fill(cx, cy - 4, cx + 1, cy + 3, AureliaPalette.ACCENT_LIGHT);
	}

	private static String metres(double length) {
		return length == Math.rint(length) ? String.valueOf((long) length) : String.format(Locale.ROOT, "%.1f", length);
	}

	private static boolean hit(double mouseX, double mouseY, int x, int y, int w, int h) {
		return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
	}

	private boolean inStage(double mouseX, double mouseY) {
		return hit(mouseX, mouseY, rightX(), stageY(), rightWidth(), height - MARGIN - stageY());
	}

	private int listWidth() {
		return Math.max(150, Math.min(220, width * 34 / 100));
	}

	private int listTop() {
		return HEADER + 28;
	}

	private double maxScroll() {
		return Math.max(0, shownTrains.size() * ROW_HEIGHT - (height - MARGIN - listTop()));
	}

	private int rightX() {
		return MARGIN + listWidth() + 14;
	}

	private int rightWidth() {
		return width - rightX() - MARGIN;
	}

	private int stripY() {
		return HEADER + 26;
	}

	private int stageY() {
		return stripY() + 38;
	}

	private int doneX() {
		return width - MARGIN - 60;
	}
}
