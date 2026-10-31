package com.aureliatransit.architecture.timetable;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import com.aureliatransit.architecture.text.TextSanitizer;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A printed timetable or line map. Sneak-right-click a platform to print one (the client reads the live station and
 * the server stores the sanitised lines on the item). Right-click in the air opens the sheet.
 */
public class PocketTimetableItem extends Item {

	public static final String TITLE = "Title";
	public static final String KIND = "Kind";
	public static final String LINES = "Lines";

	/** Client only: open the sheet. Set by the client initializer. */
	public static java.util.function.Consumer<ItemStack> openScreen = stack -> {
	};

	/** Client only: print from the platform under the cursor. Returns true when a packet was sent. */
	public static java.util.function.BiPredicate<PlayerEntity, ItemUsageContext> printAt = (player, context) -> false;

	public PocketTimetableItem(Settings settings) {
		super(settings);
	}

	public static ItemStack printed(PocketSheet.Sheet sheet) {
		final ItemStack stack = new ItemStack(com.aureliatransit.architecture.registry.ModItems.POCKET_TIMETABLE);
		write(stack, sheet);
		return stack;
	}

	public static void write(ItemStack stack, PocketSheet.Sheet sheet) {
		final NbtCompound tag = stack.getOrCreateNbt();
		tag.putString(TITLE, TextSanitizer.sanitize(sheet.title(), PocketSheet.MAX_CHARS));
		tag.putString(KIND, sheet.kind().name());
		final NbtList lines = new NbtList();
		for (final String line : sheet.lines()) {
			lines.add(NbtString.of(line));
		}
		tag.put(LINES, lines);
	}

	public static PocketSheet.Sheet read(ItemStack stack) {
		final NbtCompound tag = stack.getNbt();
		if (tag == null) {
			return new PocketSheet.Sheet("", PocketSheet.Kind.TIMETABLE, List.of());
		}
		final List<String> lines = new ArrayList<>();
		final NbtList list = tag.getList(LINES, NbtElement.STRING_TYPE);
		for (int i = 0; i < list.size(); i++) {
			lines.add(list.getString(i));
		}
		final PocketSheet.Kind kind = "MAP".equals(tag.getString(KIND)) ? PocketSheet.Kind.MAP : PocketSheet.Kind.TIMETABLE;
		return new PocketSheet.Sheet(tag.getString(TITLE), kind, lines);
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		final PlayerEntity player = context.getPlayer();
		if (player != null && player.isSneaking() && printAt.test(player, context)) {
			return ActionResult.SUCCESS;
		}
		return ActionResult.PASS;
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
		final ItemStack stack = player.getStackInHand(hand);
		if (world.isClient) {
			openScreen.accept(stack);
		}
		return TypedActionResult.success(stack, world.isClient);
	}

	@Override
	public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
		final PocketSheet.Sheet sheet = read(stack);
		tooltip.add(Text.translatable("tooltip." + AureliaTransitArchitecture.MOD_ID + ".pocket_timetable").formatted(Formatting.GRAY));
		if (!sheet.title().isEmpty()) {
			tooltip.add(Text.literal(sheet.title()).formatted(Formatting.DARK_GRAY));
		}
	}
}
