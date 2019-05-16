package com.gregbarasch.raftconsensus.messaging;

public class VoteResponseDto extends RaftMessage {

    private final boolean vote;

    public VoteResponseDto(long term, boolean vote) {
        super(term);
        this.vote = vote;
    }

    public boolean isYes() {
        return vote;
    }
}
