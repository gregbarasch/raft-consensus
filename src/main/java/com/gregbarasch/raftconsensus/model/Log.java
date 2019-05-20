package com.gregbarasch.raftconsensus.model;

import java.util.ArrayList;
import java.util.List;

public class Log {

    private List<LogEntry> log = new ArrayList<>(); // FIXME why is the first index 1....

    public void putEntries(List<LogEntry> entries) {
        final int startIndex = entries.get(0).getIndex();
        log = log.subList(0, startIndex);
        log.addAll(entries);
    }

    public LogEntry getEntry(int index) {
        return log.get(index);
    }

    public LogEntry getLastEntry() {
        final int lastIndex = log.size() - 1;
        if (lastIndex < 0) return null;
        return log.get(lastIndex);
    }

    /**
     * @param fromIndex inclusive
     * @param toIndex exclusive
     */
    public List<LogEntry> subLog(int fromIndex, int toIndex) {
        return log.subList(fromIndex, toIndex);
    }

    public int size() {
        return log.size();
    }
}
