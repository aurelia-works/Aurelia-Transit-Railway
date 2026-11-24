/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;

/**
 * Facts about this build of core. {@link #version()} is part of every plan hash (spec mod-suite-v1 §6.4,
 * editor-protocol §3): identical inputs give identical plans only with identical core code, so a client and a server
 * with different cores always disagree on hashes instead of placing a road the client never previewed.
 */
public final class CoreInfo {
    /**
     * The format of Stratos's files under {@code <world>/stratos/} (spec world-data-format §3; ADR-0028): an integer,
     * separate from the mod and protocol versions. Any change to any of those formats raises it and adds a migration
     * (F-5). Version 1 (G-9): operation records ({@code undo.OperationJson}) and undo images ({@code undo.UndoCodec}).
     * The handshake reports it ({@code Hello.formatVersion}).
     */
    public static final int FORMAT_VERSION = 1;

    private static final String VERSION = load();

    private CoreInfo() {
    }

    /** The core version, the Stratos version it was built with (such as {@code 0.1.0}). */
    public static String version() {
        return VERSION;
    }

    private static String load() {
        // Written by Gradle's processResources from the stratos.version property.
        try (InputStream in = CoreInfo.class.getResourceAsStream("core.properties")) {
            if (in == null) {
                throw new IllegalStateException("core.properties is missing from the core jar");
            }
            Properties properties = new Properties();
            properties.load(in);
            String version = properties.getProperty("version", "");
            if (version.isBlank() || version.contains("${")) {
                throw new IllegalStateException("core.properties has no version: '" + version + "'");
            }
            return version;
        } catch (IOException e) {
            throw new UncheckedIOException("Couldn't read core.properties", e);
        }
    }
}
