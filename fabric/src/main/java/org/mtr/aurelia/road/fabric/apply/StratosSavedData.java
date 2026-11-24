/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.fabric.apply;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.PersistentState;
import org.mtr.aurelia.road.CoreInfo;
import org.mtr.aurelia.road.world.WorldData;
import org.mtr.aurelia.road.fabric.StratosMod;

/**
 * The world's {@code stratos_meta} record in Minecraft's own save, {@code data/stratos_meta.dat} (world-data-format §3;
 * task F-5): {@code formatVersion}, {@code worldId} and (UI-1c) {@code stratosBlocks}, the world's mark for Stratos blocks. Its job is to hook Stratos into the world's saves: it reports
 * itself dirty while Stratos has unsaved changes, so every save (autosave, Save and Quit, {@code /save-all}, stopping)
 * calls {@link #writeNbt(NbtCompound)}, which writes Stratos's files first. A read-only world never has changes, so it is
 * never written.
 *
 * <p><b>From F-2+</b> it also stays dirty while the journal keeps runs not yet retired, so every world save reaches
 * Stratos ({@link WorldData#worldSave}): Minecraft writes its chunks right after, which is what retiring waits for.
 *
 * <p><b>The mark (UI-1c)</b> is on once any Stratos block has been placed and off when a world bake leaves none
 * ({@code EditorServer}). A new world starts without it; a record from before UI-1c doesn't say, so the world counts as
 * having them (the safe side) until a world bake finds none. A read-only world's mark is never saved.
 */
final class StratosSavedData extends PersistentState {
    static final String NAME = "stratos_meta";

    private final int formatVersion;
    private boolean stratosBlocks;
    private WorldData data;

    private StratosSavedData(int formatVersion, boolean stratosBlocks) {
        this.formatVersion = formatVersion;
        this.stratosBlocks = stratosBlocks;
    }

    static StratosSavedData create() {
        return new StratosSavedData(CoreInfo.FORMAT_VERSION, false);
    }

    static StratosSavedData load(NbtCompound tag) {
        return new StratosSavedData(tag.getInt("formatVersion"), !tag.contains("stratosBlocks") || tag.getBoolean("stratosBlocks"));
    }

    /** Whether the world has Stratos blocks, by its mark (UI-1c). */
    boolean stratosBlocks() {
        return this.stratosBlocks;
    }

    /** Sets the world's mark (UI-1c), saved with the world's next save unless the world is read-only. */
    void stratosBlocks(boolean present) {
        if (present != this.stratosBlocks) {
            this.stratosBlocks = present;
            if (this.data == null || this.data.readOnly().isEmpty()) {
                markDirty();
            }
        }
    }

    /** The {@code formatVersion} this record was saved with (this Stratos's own for a new one). */
    int formatVersion() {
        return this.formatVersion;
    }

    /** From now on, this record saves {@code data}. */
    void attach(WorldData data) {
        this.data = data;
    }

    @Override
    public boolean isDirty() {
        return super.isDirty() || (this.data != null && this.data.wantsWorldSaves());
    }

    @Override
    public NbtCompound writeNbt(NbtCompound tag) {
        if (this.data != null) {
            try {
                this.data.worldSave();
            } catch (IOException | RuntimeException e) {
                // Still unsaved: the next save tries again.
                StratosMod.LOGGER.error("[stratos] couldn't save this world's Stratos data", e);
            }
        }
        tag.putInt("formatVersion", CoreInfo.FORMAT_VERSION);
        Optional<UUID> worldId = this.data == null ? Optional.empty() : this.data.meta().map(meta -> meta.worldId());
        worldId.ifPresent(id -> tag.putUuid("worldId", id));
        tag.putBoolean("stratosBlocks", this.stratosBlocks);
        return tag;
    }
}
