package org.mtr.aurelia.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.BlockPos;
import org.mtr.mod.zip.ZipLines;
import org.mtr.mod.zip.ZipSettings;

/**
 * What this client last heard from the server. The cable renderer reads {@link #views()} and does not allocate.
 */
public final class ZipClient {

	public static final int CAPACITY = 64;

	private static ZipSettings settings = ZipSettings.DEFAULT;
	private static final View[] VIEWS = new View[CAPACITY];
	private static int count;

	static {
		for (int i = 0; i < VIEWS.length; i++) {
			VIEWS[i] = new View();
		}
	}

	private ZipClient() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(ZipLines.SYNC, (client, handler, buf, response) -> {
			final ZipSettings read = ZipLines.readSettings(buf).clamped();
			final int n = Math.min(buf.readVarInt(), CAPACITY);
			final long[] ids = new long[n];
			final BlockPos[] a = new BlockPos[n];
			final BlockPos[] b = new BlockPos[n];
			final boolean[] seats = new boolean[n];
			final float[] ts = new float[n];
			for (int i = 0; i < n; i++) {
				ids[i] = buf.readLong();
				a[i] = buf.readBlockPos();
				b[i] = buf.readBlockPos();
				seats[i] = buf.readBoolean();
				ts[i] = buf.readFloat();
			}
			client.execute(() -> apply(read, n, ids, a, b, seats, ts));
		});
	}

	public static ZipSettings settings() {
		return settings;
	}

	public static View[] views() {
		return VIEWS;
	}

	public static int count() {
		return count;
	}

	public static void request(ZipSettings next) {
		settings = next.clamped();
		final PacketByteBuf buf = PacketByteBufs.create();
		ZipLines.writeSettings(buf, settings);
		ClientPlayNetworking.send(ZipLines.SET, buf);
	}

	private static void apply(ZipSettings read, int n, long[] ids, BlockPos[] a, BlockPos[] b, boolean[] seats, float[] ts) {
		settings = read;
		final long now = System.nanoTime();
		for (int i = 0; i < n; i++) {
			final View view = VIEWS[i];
			final float shown = view.id == ids[i] ? view.shown(now) : ts[i];
			view.id = ids[i];
			view.ax = a[i].getX();
			view.ay = a[i].getY();
			view.az = a[i].getZ();
			view.bx = b[i].getX();
			view.by = b[i].getY();
			view.bz = b[i].getZ();
			view.seat = seats[i];
			view.prevT = view.ready ? shown : ts[i];
			view.targetT = ts[i];
			view.prevTime = now;
			view.ready = true;
		}
		for (int i = n; i < count; i++) {
			VIEWS[i].ready = false;
		}
		count = n;
	}

	public static final class View {
		private static final long BLEND_NANOS = 250_000_000L;

		private long id;
		private int ax;
		private int ay;
		private int az;
		private int bx;
		private int by;
		private int bz;
		private boolean seat;
		private float prevT;
		private float targetT;
		private long prevTime;
		private boolean ready;

		public int ax() {
			return ax;
		}

		public int ay() {
			return ay;
		}

		public int az() {
			return az;
		}

		public int bx() {
			return bx;
		}

		public int by() {
			return by;
		}

		public int bz() {
			return bz;
		}

		public boolean seat() {
			return seat;
		}

		public float seatT(long now) {
			return shown(now);
		}

		private float shown(long now) {
			if (!ready) {
				return targetT;
			}
			final float u = Math.min(1F, (now - prevTime) / (float) BLEND_NANOS);
			return prevT + (targetT - prevT) * u;
		}
	}
}
