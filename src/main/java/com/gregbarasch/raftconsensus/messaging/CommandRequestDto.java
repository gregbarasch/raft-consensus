package com.gregbarasch.raftconsensus.messaging;

import java.time.LocalDateTime;

public class CommandRequestDto extends RaftMessage {

    private final Object command;
    private final String commandId;

    /*
    TODO
    unique per command? or unique for every instance of every command
    Also commands are issued by client not by followers

    Safety: Yet, in order to make sure that commands are not duplicated,
    there is an additional property: Every command is paired with a unique command id,
     which assures that the leader is aware of the situation and that execution of the command
     as well as the response happen only once for every unique command.
     */
    CommandRequestDto(long term, Object command) {
        super(term);
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
