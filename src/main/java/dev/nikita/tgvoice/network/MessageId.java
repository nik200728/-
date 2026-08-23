package dev.nikita.tgvoice.network;

import java.util.UUID;

public final class MessageId {
    private MessageId() {}

    public static String newId() {
        return UUID.randomUUID().toString();
    }
}
