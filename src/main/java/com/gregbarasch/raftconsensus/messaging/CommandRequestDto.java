package com.gregbarasch.raftconsensus.messaging;

public class CommandRequestDto {

    // command type baked into the raft implementation // FIXME
    private final int amount;
    private final String requestId;

    public CommandRequestDto(int amount, String requestId) {
        this.amount = amount;
        this.requestId = requestId;
    }

    public int getAmount() {
        return amount;
    }

    public String getRequestId() {
        return requestId;
    }
}
