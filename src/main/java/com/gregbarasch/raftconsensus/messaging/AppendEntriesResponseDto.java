package com.gregbarasch.raftconsensus.messaging;

public class AppendEntriesResponseDto implements RaftResponseMessage {

    private final long term;
    private final long originalRequestTerm;
    private final boolean success;
    private final int matchIndex;

    public AppendEntriesResponseDto(long term, long originalRequestTerm, boolean success, int matchIndex) {
        this.term = term;
        this.originalRequestTerm = originalRequestTerm;
        this.success = success;
        this.matchIndex = matchIndex;
    }

    @Override
    public long getTerm() {
        return term;
    }

    @Override
    public long getOriginalRequestTerm() {
        return originalRequestTerm;
    }

    public boolean isSuccess() {
        return success;
    }

    public int getMatchIndex() {
        return matchIndex;
    }
}
