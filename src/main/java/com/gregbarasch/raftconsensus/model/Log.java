package com.gregbarasch.raftconsensus.model;

import java.util.ArrayList;
import java.util.List;

public class Log {

    // FIXME?? not necessary. index's start at -1 and 0 instead of 0 and 1
    private List<LogEntry> log = new ArrayList<>();

    public void putEntries(List<LogEntry> entries) {
        final int startIndex = entries.get(0).getIndex();

        // beginning
        List<LogEntry> beginning = log.subList(0, startIndex);

        // middle
        beginning.addAll(entries);

        // end
        // If we have new entries that didnt exist in what was sent
        final LogEntry lastNewEntry = entries.get(entries.size()-1);
        if (getLastEntry().getIndex() > lastNewEntry.getIndex()) {
            List<LogEntry> end = log.subList(lastNewEntry.getIndex()+1, getLastEntry().getIndex()+1);
            beginning.addAll(end);
        }

        // result
        log = beginning;
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
