package com.gregbarasch.raftconsensus.messaging;

public class AppendEntriesResponseDto extends RaftMessage {

    private final boolean success;

    public AppendEntriesResponseDto(long term, boolean success) {
        super(term);
        this.success = success;
    }

    public boolean isSuccess() {
        return success;
    }
}
