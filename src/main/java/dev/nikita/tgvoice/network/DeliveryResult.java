package dev.nikita.tgvoice.network;

public record DeliveryResult(String messageId, Status status, String detail) {
    public enum Status { ACCEPTED, DUPLICATE, REJECTED, FAILED }
}
