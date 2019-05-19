package com.gregbarasch.raftconsensus.messaging;

public class VoteRequestDto extends RaftMessage {

    private final Integer logIndex;
    private final Long logTerm;

    public VoteRequestDto(long term, Integer logIndex, Long logTerm) {
        super(term);
        this.logIndex = logIndex;
        this.logTerm = logTerm;
    }

    public Integer getLogIndex() {
        return logIndex;
    }

    public Long getLogTerm() {
        return logTerm;
    }
}
