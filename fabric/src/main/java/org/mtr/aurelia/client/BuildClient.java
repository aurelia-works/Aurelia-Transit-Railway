package org.mtr.aurelia.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.BlockPos;
import org.mtr.mod.building.BuildSettings;
import org.mtr.mod.building.BuildTools;

/**
 * The switches and the marks this client last heard. The preview renderer reads the fields and does not allocate.
 */
public final class BuildClient {

	private static BuildSettings settings = BuildSettings.DEFAULT;
	private static boolean stationA;
	private static int sax;
	private static int say;
	private static int saz;
	private static boolean stationB;
	private static int sbx;
	private static int sby;
	private static int sbz;
	private static boolean rail;
	private static int rx;
	private static int ry;
	private static int rz;
	private static float railYaw;
	private static boolean tunnelA;
	private static int tax;
	private static int tay;
	private static int taz;
	private static boolean tunnelB;
	private static int tbx;
	private static int tby;
	private static int tbz;

	private BuildClient() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(BuildTools.SYNC, (client, handler, buf, response) -> {
			final BuildSettings read = BuildTools.readSettings(buf).clamped();
			final boolean readStationA = buf.readBoolean();
			final BlockPos stationAPos = readStationA ? buf.readBlockPos() : null;
			final boolean readStationB = buf.readBoolean();
			final BlockPos stationBPos = readStationB ? buf.readBlockPos() : null;
			final boolean readRail = buf.readBoolean();
			final BlockPos railPos = readRail ? buf.readBlockPos() : null;
			final float readYaw = readRail ? buf.readFloat() : 0;
			final boolean readTunnelA = buf.readBoolean();
			final BlockPos tunnelAPos = readTunnelA ? buf.readBlockPos() : null;
			final boolean readTunnelB = buf.readBoolean();
			final BlockPos tunnelBPos = readTunnelB ? buf.readBlockPos() : null;
			client.execute(() -> apply(read, readStationA, stationAPos, readStationB, stationBPos, readRail, railPos, readYaw, readTunnelA, tunnelAPos, readTunnelB, tunnelBPos));
		});
	}

	public static BuildSettings settings() {
		return settings;
	}

	public static boolean stationA() {
		return stationA;
	}

	public static int sax() {
		return sax;
	}

	public static int say() {
		return say;
	}

	public static int saz() {
		return saz;
	}

	public static boolean stationB() {
		return stationB;
	}

	public static int sbx() {
		return sbx;
	}

	public static int sby() {
		return sby;
	}

	public static int sbz() {
		return sbz;
	}

	public static boolean rail() {
		return rail;
	}

	public static int rx() {
		return rx;
	}

	public static int ry() {
		return ry;
	}

	public static int rz() {
		return rz;
	}

	public static float railYaw() {
		return railYaw;
	}

	public static boolean tunnelA() {
		return tunnelA;
	}

	public static int tax() {
		return tax;
	}

	public static int tay() {
		return tay;
	}

	public static int taz() {
		return taz;
	}

	public static boolean tunnelB() {
		return tunnelB;
	}

	public static int tbx() {
		return tbx;
	}

	public static int tby() {
		return tby;
	}

	public static int tbz() {
		return tbz;
	}

	public static void request(BuildSettings next) {
		settings = next.clamped();
		final PacketByteBuf buf = PacketByteBufs.create();
		BuildTools.writeSettings(buf, settings);
		ClientPlayNetworking.send(BuildTools.SET, buf);
	}

	private static void apply(BuildSettings read, boolean readStationA, BlockPos stationAPos, boolean readStationB, BlockPos stationBPos, boolean readRail, BlockPos railPos, float readYaw, boolean readTunnelA, BlockPos tunnelAPos, boolean readTunnelB, BlockPos tunnelBPos) {
		settings = read;
		stationA = readStationA;
		if (stationAPos != null) {
			sax = stationAPos.getX();
			say = stationAPos.getY();
			saz = stationAPos.getZ();
		}
		stationB = readStationB;
		if (stationBPos != null) {
			sbx = stationBPos.getX();
			sby = stationBPos.getY();
			sbz = stationBPos.getZ();
		}
		rail = readRail;
		if (railPos != null) {
			rx = railPos.getX();
			ry = railPos.getY();
			rz = railPos.getZ();
			railYaw = readYaw;
		}
		tunnelA = readTunnelA;
		if (tunnelAPos != null) {
			tax = tunnelAPos.getX();
			tay = tunnelAPos.getY();
			taz = tunnelAPos.getZ();
		}
		tunnelB = readTunnelB;
		if (tunnelBPos != null) {
			tbx = tunnelBPos.getX();
			tby = tunnelBPos.getY();
			tbz = tunnelBPos.getZ();
		}
	}
}
