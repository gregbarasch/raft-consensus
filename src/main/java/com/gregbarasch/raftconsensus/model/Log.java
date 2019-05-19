package com.gregbarasch.raftconsensus.model;

import java.util.ArrayList;
import java.util.List;

public class Log {

    private List<LogEntry> log = new ArrayList<>(); // FIXME why is the first index 1....

    public void appendEntries(List<LogEntry> logSuffix) {
        log.addAll(logSuffix);
    }

    public void overwriteEntries(List<LogEntry> overwritingLogSuffix) {
        final int startIndex = overwritingLogSuffix.get(0).getIndex();
        log = log.subList(0, startIndex);
        log.addAll(overwritingLogSuffix);
    }

    public LogEntry getEntry(int index) {
        return log.get(index);
    }

    public LogEntry getLastEntry() {
        final int lastIndex = log.size() - 1;
        if (lastIndex < 0) return null;
        return log.get(lastIndex);
    }

    public List<LogEntry> subLog(int startIndex, int endIndex) {
        return log.subList(startIndex, endIndex);
    }

    public int size() {
        return log.size();
    }
}
