package com.gregbarasch.raftconsensus.messaging;

public class VoteResponseDto implements RaftResponseMessage {

    private final long term;
    private final long originalRequestTerm;
    private final boolean vote;

    public VoteResponseDto(long term, long originalRequestTerm, boolean vote) {
        this.term = term;
        this.originalRequestTerm = originalRequestTerm;
        this.vote = vote;
    }

    @Override
    public long getTerm() {
        return term;
    }

    @Override
    public long getOriginalRequestTerm() {
        return originalRequestTerm;
    }

    public boolean isYes() {
        return vote;
    }
}
