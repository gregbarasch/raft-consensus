package com.gregbarasch.raftconsensus.model;

import java.io.Serializable;
import java.util.Objects;

public class LogEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String command;
    private final int index;
    private final long term;

    public LogEntry(String command, int index, long term) {
        this.command = command;
        this.index = index;
        this.term = term;
    }
    public String getCommand() {
        return command;
    }

    public int getIndex() {
        return index;
    }

    public long getTerm() {
        return term;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof LogEntry)) return false;

        LogEntry entry = (LogEntry) other;
        return index == entry.index
                && term == entry.term
                && Objects.equals(command, entry.command);
    }

    @Override
    public int hashCode() {
        return Objects.hash(command, index, term);
    }
}
