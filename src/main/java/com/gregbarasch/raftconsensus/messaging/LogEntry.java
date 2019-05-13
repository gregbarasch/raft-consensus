package com.gregbarasch.raftconsensus.messaging;

public class LogEntry extends RaftMessage {

    private final Object command;
    private final int index; // FIXME for some reason its supposed to start from 1? double check

    public LogEntry(Object command, int index, long term) {
        super(term);
        this.command = command;
        this.index = index;
    }

    public int getIndex() {
        return index;
    }

    public Object getCommand() {
        return command;
    }
}
