package com.gregbarasch.raftconsensus.model;

import java.util.ArrayList;
import java.util.List;

public class Log {

    private List<LogEntry> log = new ArrayList<>();
    private int commitIndex = 0;
    private int lastApplied = 0;

    public void appendEntries(List<LogEntry> logSuffix) {
        log.addAll(logSuffix);
    }

    public void overwriteEntries(List<LogEntry> overwritingLogSuffix) {
        final int startIndex = overwritingLogSuffix.get(0).getIndex();
        log = log.subList(0, startIndex);
        log.addAll(overwritingLogSuffix);
    }

    public LogEntry getLastEntry() {
        final int lastIndex = log.size() - 1;
        if (lastIndex < 0) return null;
        return log.get(lastIndex);
    }

    public int getCommitIndex() {
        return commitIndex;
    }

    public void setCommitIndex(int commitIndex) {
        this.commitIndex = commitIndex;
    }

    public int getLastApplied() {
        return lastApplied;
    }

    public void setLastApplied(int lastApplied) {
        this.lastApplied = lastApplied;
    }
}
