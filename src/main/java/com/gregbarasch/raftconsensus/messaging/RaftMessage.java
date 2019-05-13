package com.gregbarasch.raftconsensus.messaging;

public class RaftMessage {

    private final long term;

    RaftMessage(long term) {
        this.term = term;
    }

    public long getTerm() {
        return term;
    }
}
