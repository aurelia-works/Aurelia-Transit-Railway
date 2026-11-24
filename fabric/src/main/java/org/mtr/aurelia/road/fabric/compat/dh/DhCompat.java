/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.fabric.compat.dh;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.chunk.WorldChunk;
import org.mtr.aurelia.road.fabric.StratosMod;
import org.mtr.aurelia.road.spi.ChunkXZ;

/**
 * Distant Horizons is optional (Stratos spec mod-suite-v1 §6.10, task F-7). When an edit has changed blocks, DH is told
 * about each of its chunks with {@code overwriteChunkDataAsync}, so its far view shows the edit within seconds instead of
 * whenever DH notices by itself (Stratos spike S8: 3.8–9.1 s with the call, 2.5–27.5 s without).
 *
 * <p>ATR has no DH dependency, not even a compile-only one: its API is found by name at runtime, and only when the
 * {@code distanthorizons} mod is loaded. The methods are looked up on DH's public API interfaces (version 2's
 * {@code DhApi.Delayed}, {@code IDhApiWorldProxy}, {@code IDhApiLevelWrapper}, {@code IDhApiTerrainDataRepo} and
 * {@code DhApiResult}), never on its own classes. A DH whose API doesn't match is logged once and left alone; DH still
 * catches up by itself.
 *
 * <p>Server thread: called from the apply engine while the edit's chunks are still loaded, after every apply, undo,
 * redo, delete, rebuild and crash repair. Never touches DH's own files.
 */
public final class DhCompat {
    public static final boolean LOADED = FabricLoader.getInstance().isModLoaded("distanthorizons");
    private static final String API = "com.seibel.distanthorizons.api.";
    private static boolean warned;
    /** DH's API, found on the first edit; null before then, or when it isn't there or doesn't match. */
    private static Api api;
    private static boolean looked;
    /** Chunks DH took, and edits it didn't take in full, since the game started (for the log). */
    private static final AtomicLong CHUNKS_TOLD = new AtomicLong();
    private static final AtomicLong EDITS_REFUSED = new AtomicLong();

    private DhCompat() {
    }

    /** How many chunks DH has taken {@code overwriteChunkDataAsync} calls for since the game started. */
    public static long chunksTold() {
        return CHUNKS_TOLD.get();
    }

    /** How many edits DH didn't take in full since the game started. */
    public static long editsRefused() {
        return EDITS_REFUSED.get();
    }

    /** Tells DH that blocks changed in {@code chunks} of {@code world}; nothing without DH. Never throws. */
    public static void chunksChanged(ServerWorld world, Collection<ChunkXZ> chunks) {
        if (!LOADED || chunks.isEmpty()) {
            return;
        }
        String problem;
        try {
            if (!looked) {
                looked = true;
                api = Api.find();
            }
            problem = api == null ? null : api.chunksChanged(world, chunks);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            problem = e instanceof InvocationTargetException target && target.getCause() != null ? target.getCause().toString() : e.toString();
        }
        if (problem != null) {
            EDITS_REFUSED.incrementAndGet();
            if (!warned) {
                warned = true; // once per game: DH catches up by itself, so this is only for the log
                StratosMod.LOGGER.warn("[stratos] couldn't tell Distant Horizons about an edit in {} ({}); its far view will catch up by itself",
                        world.getRegistryKey().getValue(), problem);
            }
        }
    }

    /** DH's API methods, found by name. */
    private record Api(Field worldProxy, Field terrainRepo, Method worldLoaded, Method levelWrappers, Method wrappedObject, Method overwrite,
            Field success, Field message) {
        /** The API, or null (logged once) when DH's doesn't have these names. */
        static Api find() {
            try {
                ClassLoader loader = DhCompat.class.getClassLoader();
                Class<?> delayed = Class.forName(API + "DhApi$Delayed", true, loader);
                Class<?> proxy = Class.forName(API + "interfaces.world.IDhApiWorldProxy", false, loader);
                Class<?> wrapper = Class.forName(API + "interfaces.world.IDhApiLevelWrapper", false, loader);
                Class<?> repo = Class.forName(API + "interfaces.data.IDhApiTerrainDataRepo", false, loader);
                Class<?> result = Class.forName(API + "objects.DhApiResult", false, loader);
                return new Api(delayed.getField("worldProxy"), delayed.getField("terrainRepo"), proxy.getMethod("worldLoaded"),
                        proxy.getMethod("getAllLoadedLevelWrappers"), wrapper.getMethod("getWrappedMcObject"),
                        repo.getMethod("overwriteChunkDataAsync", wrapper, Object[].class), result.getField("success"), result.getField("message"));
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                warned = true;
                StratosMod.LOGGER.warn("[stratos] Distant Horizons is installed but its API isn't the one Stratos knows ({}); its far view will catch up by itself", e.toString());
                return null;
            }
        }

        /** Null when every call was taken; else what went wrong first. */
        String chunksChanged(ServerWorld world, Collection<ChunkXZ> chunks) throws ReflectiveOperationException {
            Object proxy = this.worldProxy.get(null);
            Object repo = this.terrainRepo.get(null);
            if (proxy == null || repo == null || !(Boolean) this.worldLoaded.invoke(proxy)) {
                return "no DH world is loaded";
            }
            Object wrapper = null;
            for (Object candidate : (Iterable<?>) this.levelWrappers.invoke(proxy)) {
                if (this.wrappedObject.invoke(candidate) == world) {
                    wrapper = candidate;
                    break;
                }
            }
            if (wrapper == null) {
                return "DH has no level for it";
            }
            String problem = null;
            for (ChunkXZ chunk : chunks) {
                WorldChunk loaded = world.getChunkManager().getWorldChunk(chunk.x(), chunk.z()); // never waits: they are retained
                if (loaded == null) {
                    continue;
                }
                Object taken = this.overwrite.invoke(repo, wrapper, new Object[] {loaded, world});
                if (taken != null && (Boolean) this.success.get(taken)) {
                    CHUNKS_TOLD.incrementAndGet();
                } else if (problem == null) {
                    problem = taken == null ? "DH answered nothing" : String.valueOf(this.message.get(taken));
                }
            }
            return problem;
        }
    }
}
