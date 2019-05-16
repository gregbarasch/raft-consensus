package com.gregbarasch.raftconsensus.messaging;

import akka.actor.ActorRef;

public class CommandResponseDto extends RaftMessage {

    private final boolean isLeader;
    private final ActorRef leaderRedirect;

    public CommandResponseDto(long term) {
        this(term, true, null);
    }

    public CommandResponseDto(long term, boolean isLeader, ActorRef leaderRedirect) {
        super(term);
        this.isLeader = isLeader;
        this.leaderRedirect = leaderRedirect;
    }

    public boolean isLeader() {
        return isLeader;
    }

    public ActorRef getLeaderRedirect() {
        return leaderRedirect;
    }
}
