package org.mtr.mod.screen;

import org.mtr.mapping.holder.*;
import org.mtr.mapping.mapper.ButtonWidgetExtension;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mapping.mapper.PlayerHelper;
import org.mtr.mod.InitClient;
import org.mtr.mod.client.IDrawing;
import org.mtr.mod.data.IGui;
import org.mtr.mod.data.TicketCurrency;
import org.mtr.mod.data.TicketRules;
import org.mtr.mod.generated.lang.TranslationProvider;
import org.mtr.mod.packet.PacketAddBalance;

public class TicketMachineScreen extends MTRScreenBase implements IGui {

	private final ButtonWidgetExtension[] buttons = new ButtonWidgetExtension[BUTTON_COUNT];
	private final MutableText balanceText;
	/** Item id the server will actually remove. Emerald keeps the original wording. */
	private String currencyId = TicketRules.DEFAULT_CURRENCY_ID;

	private static final int BUTTON_COUNT = 10;
	private static final int BUTTON_WIDTH = 80;

	public TicketMachineScreen(int balance) {
		super();

		for (int i = 0; i < BUTTON_COUNT; i++) {
			final int index = i;
			buttons[i] = new ButtonWidgetExtension(0, 0, 0, SQUARE_SIZE, TranslationProvider.GUI_MTR_ADD_VALUE.getMutableText(), button -> {
				InitClient.REGISTRY_CLIENT.sendPacketToServer(new PacketAddBalance(index));
				MinecraftClient.getInstance().openScreen(null);
			});
		}

		balanceText = TranslationProvider.GUI_MTR_BALANCE.getMutableText(balance);
	}

	public void setCurrencyId(String currencyId) {
		this.currencyId = currencyId == null || currencyId.isBlank() ? TicketRules.DEFAULT_CURRENCY_ID : currencyId;
	}

	@Override
	protected void init2() {
		super.init2();

		for (int i = 0; i < BUTTON_COUNT; i++) {
			IDrawing.setPositionAndWidth(buttons[i], width - BUTTON_WIDTH, SQUARE_SIZE * (i + 1), BUTTON_WIDTH - TEXT_FIELD_PADDING);
		}

		for (final ButtonWidgetExtension button : buttons) {
			addChild(new ClickableWidget(button));
		}
	}

	@Override
	public void tick2() {
		final int emeraldCount = getEmeraldCount();
		for (int i = 0; i < BUTTON_COUNT; i++) {
			buttons[i].active = emeraldCount >= Math.pow(2, i);
		}
	}

	@Override
	public void render(GraphicsHolder graphicsHolder, int mouseX, int mouseY, float delta) {
		renderBackground(graphicsHolder);
		final MutableText emeraldsText = currencyCountText(getEmeraldCount());
		graphicsHolder.drawText(balanceText, TEXT_PADDING, TEXT_PADDING, ARGB_WHITE, false, GraphicsHolder.getDefaultLight());
		graphicsHolder.drawText(emeraldsText, width - TEXT_PADDING - GraphicsHolder.getTextWidth(emeraldsText), TEXT_PADDING, ARGB_WHITE, false, GraphicsHolder.getDefaultLight());

		for (int i = 0; i < BUTTON_COUNT; i++) {
			graphicsHolder.drawText(addBalanceText(i), TEXT_PADDING, (i + 1) * SQUARE_SIZE + TEXT_PADDING, ARGB_WHITE, false, GraphicsHolder.getDefaultLight());
		}

		super.render(graphicsHolder, mouseX, mouseY, delta);
	}

	@Override
	public boolean isPauseScreen2() {
		return false;
	}

	private int getEmeraldCount() {
		final ClientPlayerEntity clientPlayerEntity = MinecraftClient.getInstance().getPlayerMapped();
		final PlayerInventory playerInventory = clientPlayerEntity == null ? null : PlayerHelper.getPlayerInventory(new PlayerEntity(clientPlayerEntity.data));
		if (playerInventory == null) {
			return 0;
		}
		final Item item = TicketRules.DEFAULT_CURRENCY_ID.equals(currencyId) ? Items.getEmeraldMapped() : TicketCurrency.item(currencyId);
		return item == null ? 0 : playerInventory.count(item);
	}

	/** Null while the currency is still emeralds, so the original sentences stay. */
	private String currencyName() {
		if (TicketRules.DEFAULT_CURRENCY_ID.equals(currencyId)) {
			return null;
		}
		final Item item = TicketCurrency.item(currencyId);
		return item == null ? currencyId : item.getName().getString();
	}

	private MutableText currencyCountText(int count) {
		final String name = currencyName();
		if (name == null) {
			return TranslationProvider.GUI_MTR_EMERALDS.getMutableText(count);
		}
		return TranslationProvider.GUI_MTR_TICKET_CURRENCY.getMutableText(name, count);
	}

	private MutableText addBalanceText(int index) {
		final int cost = (int) Math.pow(2, index);
		final String name = currencyName();
		if (name == null) {
			return TranslationProvider.GUI_MTR_ADD_BALANCE_FOR_EMERALDS.getMutableText(PacketAddBalance.getAddAmount(index), cost);
		}
		return TranslationProvider.GUI_MTR_ADD_BALANCE_FOR_CURRENCY.getMutableText(PacketAddBalance.getAddAmount(index), cost, name);
	}
}
