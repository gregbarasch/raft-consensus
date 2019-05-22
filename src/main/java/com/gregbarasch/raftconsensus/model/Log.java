package com.gregbarasch.raftconsensus.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Log implements Serializable {

    private static final long serialVersionUID = 1L;

    // FIXME?? not necessary. index's start at -1 and 0 instead of 0 and 1
    private List<LogEntry> log = new ArrayList<>();

    public void putEntries(List<LogEntry> entries) {

        final int startIndex = entries.get(0).getIndex();
        final int endIndex = entries.get(entries.size()-1).getIndex();

        int entryIndex = -1; // This will be our current position between start and end inclusive
        int currIndex = 0; // This will be the index counter starting from 0

        List<LogEntry> resultLog = log.subList(0, startIndex);

        // We are going to make sure each entry matches
        for (final LogEntry entry : entries) {
            entryIndex = entry.getIndex();

            // if our index is in bounds and our log doesnt match, break early
            if (entryIndex < log.size() && !log.get(entryIndex).equals(entry)) {
                break;
            }

            // otherwise just add back the matching entry
            resultLog.add(entry);
            currIndex++;
        }

        // Now, if we have entries from breaking early, append them
        if (entryIndex != endIndex) {
            final List<LogEntry> remainingEntries = entries.subList(currIndex, entries.size());
            resultLog.addAll(remainingEntries);
        } else if (endIndex < log.size()-1) {
            // otherwise if we have some additional entries in our log, append those
            final List<LogEntry> remainingEntries = log.subList(endIndex, log.size());
            resultLog.addAll(remainingEntries);
        }

        // Needed for serializability... // FIXME optimize?
        log = new ArrayList<>(resultLog);
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
