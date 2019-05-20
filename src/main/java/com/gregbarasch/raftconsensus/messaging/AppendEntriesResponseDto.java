package com.gregbarasch.raftconsensus.messaging;

public class AppendEntriesResponseDto extends RaftMessage {

    private final boolean success;
    private final int matchIndex;

    public AppendEntriesResponseDto(long term, boolean success, int matchIndex) {
        super(term);
        this.success = success;
        this.matchIndex = matchIndex;
    }

    public boolean isSuccess() {
        return success;
    }

    public int getMatchIndex() {
        return matchIndex;
    }
}
