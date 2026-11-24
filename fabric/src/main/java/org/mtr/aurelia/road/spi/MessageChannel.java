/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.spi;

import org.mtr.aurelia.road.proto.Message;

/**
 * One connection's end of the {@code stratos:main} channel (spec mod-suite-v1 §5): core hands it records, and the
 * platform encodes them with {@code Messages.encode} and frames the bytes on the channel. On the server there is one per
 * player; on the client one, to the server. Incoming bytes go the other way: the platform passes them to core's
 * {@code EditorServer.receive} or {@code EditorClient.receive}, which decode and check them.
 */
@FunctionalInterface
public interface MessageChannel {
    void send(Message message);
}
