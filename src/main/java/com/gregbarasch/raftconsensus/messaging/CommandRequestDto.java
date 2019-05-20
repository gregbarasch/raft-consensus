package com.gregbarasch.raftconsensus.messaging;

import java.time.LocalDateTime;

public class CommandRequestDto {

    private final Object command;
    private final String commandId; // FIXME will need to make sure that command is unique and not re-run

    public CommandRequestDto(Object command) {
        this.command = command;
        this.commandId = LocalDateTime.now().toString() + command.hashCode();
    }


    public Object getCommand() {
        return command;
    }

    public String getCommandId() {
        return commandId;
    }
}
