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
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mod.InitClient;
import org.mtr.mod.client.CustomResourceLoader;
import org.mtr.mod.client.MinecraftClientData;
import org.mtr.mod.packet.PacketUpdateData;
import org.mtr.mod.resource.VehicleResource;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * ATR's train picker for a siding: the trains on the left, the siding's train car by car on the right, and a 3D preview
 * on a piece of track at the bottom. Hovering a train in the list shows that car; otherwise the whole train is shown.
 * As in MTR 4.1, the cars of one train family (the pack's {@code family:} tag) fold under one row, and the search also
 * matches the pack's other tags (country, type, doors, brand). Saves the same siding data as MTR's selector, which
 * comes back when the "Train picker" Look switch is off.
 */
public final class TrainPickerScreen extends Screen {

	private static final int MARGIN = 12;
	private static final int ROW_HEIGHT = 24;
	private static final int HEADER = 30;
	private static final int BUTTON_HEIGHT = 16;
	private static final long OPEN_MILLIS = 240;
	private static final int STAGE_TOP = 0xFF20262E;
	private static final int STAGE_BOTTOM = 0xFF101317;
	/** The header bar's height: the title and the Done button sit in it, the search box just under it. */
	private static final int HEADER_BAR = 26;
	private static final int FAMILY_INDENT = 8;
	/** Families opened in the list, kept while the game runs so the picker reopens the way it was left. */
	private static final Set<String> OPEN_FAMILIES = new HashSet<>();

	private final Siding siding;
	@Nullable
	private final Screen previous;
	private final List<VehicleResource> allTrains = new ObjectArrayList<>();
	/** Each train's {@code family:} tag; a train without one stands on its own row. */
	private final Map<String, String> familyOfTrain = new HashMap<>();
	/** Each train's tag values in lower case, which the search also looks through. */
	private final Map<String, String> tagTextOfTrain = new HashMap<>();
	/** The list as drawn: family rows and train rows. */
	private final List<Row> rows = new ObjectArrayList<>();
	private final long openedAt = AureliaMotion.now();

	private TextFieldWidget search;
	private double listScroll;
	private float dragYaw;
	private boolean dragging;
	@Nullable
	private VehicleResource hoveredTrain;
	@Nullable
	private String hoveredFamily;
	private int hoveredCar = -1;

	public TrainPickerScreen(Siding siding, @Nullable Screen previous) {
		super(Text.translatable("gui.aurelia.train_picker.title"));
		this.siding = siding;
		this.previous = previous;
		CustomResourceLoader.iterateVehicles(siding.getTransportMode(), allTrains::add);
		readTags(CustomResourceLoader.getVehicleTags(siding.getTransportMode()));
	}

	/** Reads MTR's tag index, tag key to tag value to vehicle ids, once when the picker opens. */
	private void readTags(@Nullable Object2ObjectAVLTreeMap<String, Object2ObjectAVLTreeMap<String, ObjectArrayList<String>>> tags) {
		if (tags == null) {
			return;
		}
		for (final Map.Entry<String, Object2ObjectAVLTreeMap<String, ObjectArrayList<String>>> key : tags.entrySet()) {
			for (final Map.Entry<String, ObjectArrayList<String>> value : key.getValue().entrySet()) {
				final String lowerValue = value.getKey().toLowerCase(Locale.ROOT);
				for (final String vehicleId : value.getValue()) {
					if (key.getKey().equals("family")) {
						familyOfTrain.put(vehicleId, value.getKey());
					}
					tagTextOfTrain.merge(vehicleId, lowerValue, (a, b) -> a + " " + b);
				}
			}
		}
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
		renderBackdrop(context);
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

	/**
	 * An opaque dark face over the whole screen, in place of the vanilla background, which lets the world (or the dirt
	 * texture) show through behind the list and the stage. Drawn before the open animation's slide, so it stays put.
	 */
	private void renderBackdrop(DrawContext context) {
		context.fill(0, 0, width, height, AureliaPalette.SURFACE);
		context.fill(0, 0, width, HEADER_BAR, STAGE_BOTTOM);
		context.fill(0, HEADER_BAR - 1, width, HEADER_BAR, AureliaPalette.LINE);
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
		hoveredFamily = null;
		if (rows.isEmpty()) {
			context.drawCenteredTextWithShadow(textRenderer, Text.translatable(allTrains.isEmpty() ? "gui.aurelia.train_picker.nothing" : "gui.aurelia.train_picker.no_match"), x + w / 2, top + 20, AureliaPalette.TEXT_DIM);
			return;
		}
		listScroll = Math.max(0, Math.min(listScroll, maxScroll()));
		final double remaining = remainingLength();
		context.enableScissor(x, top, x + w, bottom);
		for (int i = 0; i < rows.size(); i++) {
			final int rowY = top + i * ROW_HEIGHT - (int) listScroll;
			if (rowY + ROW_HEIGHT < top || rowY > bottom) {
				continue;
			}
			final Row row = rows.get(i);
			final boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= Math.max(top, rowY) && mouseY < Math.min(bottom, rowY + ROW_HEIGHT - 2);
			if (row.train == null) {
				if (hovered) {
					hoveredFamily = row.family;
				}
				renderFamilyRow(context, row, x, rowY, w, hovered);
				continue;
			}
			final VehicleResource train = row.train;
			final int rowX = x + (row.family == null ? 0 : FAMILY_INDENT);
			final int rowW = w - (rowX - x);
			final boolean fits = fits(train, remaining);
			if (hovered) {
				hoveredTrain = train;
			}
			AureliaChrome.rounded(context, rowX, rowY, rowW, ROW_HEIGHT - 2, 5, hovered && fits ? AureliaPalette.RAISED_HOVER : AureliaPalette.RAISED);
			context.fill(rowX + 4, rowY + 5, rowX + 7, rowY + ROW_HEIGHT - 7, 0xFF000000 | train.getColor());
			final String length = fits ? Text.translatable("gui.aurelia.train_picker.length", metres(train.getLength())).getString() : Text.translatable("gui.aurelia.train_picker.too_long").getString();
			final int lengthWidth = textRenderer.getWidth(length);
			context.drawText(textRenderer, ellipsis(train.getName().getString(), rowW - 24 - lengthWidth), rowX + 12, rowY + 7, fits ? AureliaPalette.FG : AureliaPalette.TEXT_DIM, false);
			context.drawText(textRenderer, length, rowX + rowW - 8 - lengthWidth, rowY + 7, fits ? AureliaPalette.TEXT_DIM : AureliaPalette.WARN, false);
			if (hovered && fits) {
				plus(context, rowX + rowW - 16 - lengthWidth, rowY + 11);
			}
		}
		context.disableScissor();
		if (maxScroll() > 0) {
			final int trackHeight = bottom - top;
			final int thumb = Math.max(16, trackHeight * trackHeight / (rows.size() * ROW_HEIGHT));
			final int thumbY = top + (int) ((trackHeight - thumb) * listScroll / maxScroll());
			AureliaChrome.rounded(context, x + w + 3, thumbY, 3, thumb, 1, AureliaPalette.LINE);
		}
	}

	/** A family's row: its name, how many cars it holds, and an arrow that shows whether it is open. */
	private void renderFamilyRow(DrawContext context, Row row, int x, int rowY, int w, boolean hovered) {
		final boolean open = OPEN_FAMILIES.contains(row.family);
		AureliaChrome.rounded(context, x, rowY, w, ROW_HEIGHT - 2, 5, hovered ? AureliaPalette.RAISED_HOVER : AureliaPalette.SURFACE);
		AureliaChrome.roundedFrame(context, x, rowY, w, ROW_HEIGHT - 2, 5, AureliaPalette.LINE);
		chevron(context, x + 6, rowY + 7, open);
		final String count = String.valueOf(row.count);
		final int countWidth = textRenderer.getWidth(count);
		context.drawText(textRenderer, ellipsis(row.label, w - 28 - countWidth), x + 16, rowY + 7, AureliaPalette.FG, false);
		context.drawText(textRenderer, count, x + w - 8 - countWidth, rowY + 7, AureliaPalette.TEXT_DIM, false);
	}

	/** The text cut to fit, ending in "..." when cut, so a clipped name reads as clipped. */
	private String ellipsis(String text, int width) {
		return textRenderer.getWidth(text) <= width ? text : textRenderer.trimToWidth(text, width - textRenderer.getWidth("...")) + "...";
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
			if (hoveredFamily != null) {
				click();
				if (!OPEN_FAMILIES.remove(hoveredFamily)) {
					OPEN_FAMILIES.add(hoveredFamily);
				}
				filter();
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

	/**
	 * Rebuilds the rows. A search lists every match flat; matching the name, the id or any tag value. Without one, the
	 * cars of a family fold under one row in the order MTR lists them, and a family of one car stays a plain row.
	 */
	private void filter() {
		final String query = search == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
		rows.clear();
		if (!query.isEmpty()) {
			for (final VehicleResource train : allTrains) {
				if (train.getName().getString().toLowerCase(Locale.ROOT).contains(query) || train.getId().toLowerCase(Locale.ROOT).contains(query) || tagTextOfTrain.getOrDefault(train.getId(), "").contains(query)) {
					rows.add(new Row(null, train, null, 0));
				}
			}
			return;
		}
		final Map<String, List<VehicleResource>> families = new LinkedHashMap<>();
		for (final VehicleResource train : allTrains) {
			final String family = familyOfTrain.get(train.getId());
			// a train with no family gets a key of its own, so it keeps its place in MTR's order
			families.computeIfAbsent(family == null ? "\0" + train.getId() : family, key -> new ObjectArrayList<>()).add(train);
		}
		for (final Map.Entry<String, List<VehicleResource>> entry : families.entrySet()) {
			final List<VehicleResource> trains = entry.getValue();
			if (trains.size() == 1) {
				rows.add(new Row(null, trains.get(0), null, 0));
				continue;
			}
			final String family = entry.getKey();
			rows.add(new Row(family, null, familyLabel(trains, family), trains.size()));
			if (OPEN_FAMILIES.contains(family)) {
				for (final VehicleResource train : trains) {
					rows.add(new Row(family, train, null, 0));
				}
			}
		}
	}

	/**
	 * What the cars' names share, such as "SP1900" from "SP1900 Trailer (Normal)" and "SP1900 Cab (Forwards, Normal)".
	 * Only names that start with the first car's first word count, so "Christmas SP1900 Trailer" does not cut it to
	 * nothing. Falls back to the first car's name.
	 */
	private static String familyLabel(List<VehicleResource> trains, String family) {
		final String first = trains.get(0).getName().getString();
		final int space = first.indexOf(' ');
		final String firstWord = space < 0 ? first : first.substring(0, space);
		String prefix = first;
		for (final VehicleResource train : trains) {
			final String name = train.getName().getString();
			if (name.startsWith(firstWord)) {
				int length = 0;
				while (length < prefix.length() && length < name.length() && prefix.charAt(length) == name.charAt(length)) {
					length++;
				}
				prefix = prefix.substring(0, length);
			}
		}
		final String label = prefix.replaceAll("[\\s(,]+$", "");
		return label.isEmpty() ? (first.isEmpty() ? family : first) : label;
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

	/** A small arrow, pointing right when the family is shut and down when it is open. */
	private static void chevron(DrawContext context, int x, int y, boolean open) {
		for (int i = 0; i < 4; i++) {
			if (open) {
				context.fill(x + i, y + 2 + i, x + 7 - i, y + 3 + i, AureliaPalette.TEXT_DIM);
			} else {
				context.fill(x + 1 + i, y + i, x + 2 + i, y + 7 - i, AureliaPalette.TEXT_DIM);
			}
		}
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
		return Math.max(0, rows.size() * ROW_HEIGHT - (height - MARGIN - listTop()));
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

	/** A family row when {@code train} is null; otherwise a train, under {@code family} when it is folded into one. */
	private record Row(@Nullable String family, @Nullable VehicleResource train, @Nullable String label, int count) {
	}
}
