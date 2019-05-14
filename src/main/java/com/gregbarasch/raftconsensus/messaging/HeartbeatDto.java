package com.gregbarasch.raftconsensus.messaging;

import com.gregbarasch.raftconsensus.model.LogEntry;

import java.util.List;

// SO the firrst suffix is empty, but we will get the last index
public class HeartbeatDto extends RaftMessage {

    private final int commitIndex;
    private final List<LogEntry> logSuffix;

    public HeartbeatDto(long term, int latestCommitIndex, List<LogEntry> logSuffix) {
        super(term);
        this.commitIndex = latestCommitIndex;
        this.logSuffix = logSuffix;
    }

    public int getCommitIndex() {
        return commitIndex;
    }

    public List<LogEntry> getLogSuffix() {
        return logSuffix;
    }
}
