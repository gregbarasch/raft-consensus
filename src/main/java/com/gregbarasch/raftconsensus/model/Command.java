package com.gregbarasch.raftconsensus.model;

import akka.actor.ActorRef;

import java.io.Serializable;

public class Command implements Serializable {

    private static final long serialVersionUID = 1L;

    // FIXME command type baked into the raft implementation
    private final String command;
    private final String commandId;
    private final ActorRef requestor;

    public Command(String command, String commandId, ActorRef requestor) {
        this.command = command;
        this.commandId = commandId;
        this.requestor = requestor;
    }

    public String getCommand() {
        return command;
    }

    public String getCommandId() {
        return commandId;
    }

    public ActorRef getRequestor() {
        return requestor;
    }
}