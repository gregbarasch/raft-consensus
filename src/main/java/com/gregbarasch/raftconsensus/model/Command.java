package com.gregbarasch.raftconsensus.model;


public class Command {

    // command type baked into the raft implementation // FIXME
    private final int amount;
    private final String commandId;

    public Command(int amount, String commandId) {
        this.amount = amount;
        this.commandId = commandId;
    }

    public int getAmount() {
        return amount;
    }

    public String getCommandId() {
        return commandId;
    }
}