package com.gregbarasch.raftconsensus.model;

public class LogEntry {

    private final Object command;
    private final int index;
    private final long term;

    public LogEntry(Object command, int index, long term) {
        this.command = command;
        this.index = index;
        this.term = term;
    }
    public Object getCommand() {
        return command;
    }

    public int getIndex() {
        return index;
    }

    public long getTerm() {
        return term;
    }
}
