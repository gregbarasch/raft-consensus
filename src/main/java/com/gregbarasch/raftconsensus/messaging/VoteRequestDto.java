package com.gregbarasch.raftconsensus.messaging;

public class VoteRequestDto extends RaftMessage {
    // FIXME vote only granted to nodes with more up to date logs...
    private final int logIndex;
    private final int logTerm;

    public VoteRequestDto(long term) {
        super(term);
    }
}
