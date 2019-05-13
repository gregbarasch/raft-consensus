package com.gregbarasch.raftconsensus.messaging;

public class VoteDto extends RaftMessage {

    private final boolean vote;

    public VoteDto(long term, boolean vote) {
        super(term);
        this.vote = vote;
    }

    public boolean isYes() {
        return vote;
    }
}
