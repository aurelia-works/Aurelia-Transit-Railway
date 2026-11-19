package com.aureliatransit.architecture.block.entity;

import com.aureliatransit.architecture.bridge.span.BridgeMember;
import com.aureliatransit.architecture.registry.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * The block a {@code BridgeMemberBlock} stands in for (its "skin", a block state string, or empty) and the members it draws.
 * Saved and sent to clients whole; a bridge's members never change after it is built, only when it is built again.
 */
public class BridgeMemberBlockEntity extends BlockEntity {

	public static final String SKIN_KEY = "Skin";
	public static final String MEMBERS_KEY = "Members";
	/** A malformed or hand-edited entity never draws more than this. */
	public static final int MAX_MEMBERS = 1024;

	private String skin = "";
	private final List<BridgeMember> members = new ArrayList<>();
	/** Furthest any member reaches from the block, for the renderer's culling box. */
	private float reach;

	public BridgeMemberBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.BRIDGE_MEMBER, pos, state);
	}

	public String skin() {
		return skin;
	}

	public List<BridgeMember> members() {
		return members;
	}

	public float reach() {
		return reach;
	}

	/** The block entity's saved form, as the bridge builder writes it into a new block. */
	public static NbtCompound encode(String skin, List<BridgeMember> members) {
		final NbtCompound nbt = new NbtCompound();
		nbt.putString(SKIN_KEY, skin == null ? "" : skin);
		final NbtList list = new NbtList();
		for (final BridgeMember member : members) {
			final NbtCompound entry = new NbtCompound();
			entry.putByte("m", (byte) member.material().ordinal());
			entry.putIntArray("p", new int[]{
					Float.floatToIntBits(member.x0()), Float.floatToIntBits(member.y0()), Float.floatToIntBits(member.z0()),
					Float.floatToIntBits(member.x1()), Float.floatToIntBits(member.y1()), Float.floatToIntBits(member.z1()),
					Float.floatToIntBits(member.thickness())});
			list.add(entry);
		}
		nbt.put(MEMBERS_KEY, list);
		return nbt;
	}

	@Override
	protected void writeNbt(NbtCompound nbt) {
		super.writeNbt(nbt);
		nbt.copyFrom(encode(skin, members));
	}

	@Override
	public void readNbt(NbtCompound nbt) {
		super.readNbt(nbt);
		skin = nbt.getString(SKIN_KEY);
		members.clear();
		reach = 1;
		final BridgeMember.Material[] materials = BridgeMember.Material.values();
		final NbtList list = nbt.getList(MEMBERS_KEY, NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < list.size() && members.size() < MAX_MEMBERS; i++) {
			final NbtCompound entry = list.getCompound(i);
			final int material = entry.getByte("m");
			final int[] p = entry.getIntArray("p");
			if (p.length != 7 || material < 0 || material >= materials.length) {
				continue;
			}
			final float[] f = new float[7];
			boolean finite = true;
			for (int k = 0; k < 7; k++) {
				f[k] = Float.intBitsToFloat(p[k]);
				finite &= Float.isFinite(f[k]) && Math.abs(f[k]) < 512;
			}
			if (!finite) {
				continue;
			}
			members.add(new BridgeMember(materials[material], f[0], f[1], f[2], f[3], f[4], f[5], Math.max(0.02f, Math.min(4f, f[6]))));
			for (int k = 0; k < 6; k++) {
				reach = Math.max(reach, Math.abs(f[k]) + 1);
			}
		}
	}

	@Override
	public Packet<ClientPlayPacketListener> toUpdatePacket() {
		return BlockEntityUpdateS2CPacket.create(this);
	}

	@Override
	public NbtCompound toInitialChunkDataNbt() {
		return createNbt();
	}
}
