package com.gregbarasch.raftconsensus.messaging;

public class VoteRequestDto implements RaftMessage {

    private final long term;
    private final Integer logIndex;
    private final Long logTerm;

    public VoteRequestDto(long term, Integer logIndex, Long logTerm) {
        this.term = term;
        this.logIndex = logIndex;
        this.logTerm = logTerm;
    }

    @Override
    public long getTerm() {
        return term;
    }

    public Integer getLogIndex() {
        return logIndex;
    }

    public Long getLogTerm() {
        return logTerm;
    }
}
