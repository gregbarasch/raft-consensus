package com.gregbarasch.raftconsensus.messaging;

public class CommandRequestDto {

    // command type baked into the raft implementation // FIXME
    private final String command;
    private final String requestId;

    public CommandRequestDto(String command, String requestId) {
        this.command = command;
        this.requestId = requestId;
    }

    public String getCommand() {
        return command;
    }

    public String getRequestId() {
        return requestId;
    }
}
