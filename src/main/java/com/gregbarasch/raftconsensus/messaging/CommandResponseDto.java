package com.gregbarasch.raftconsensus.messaging;

import akka.actor.ActorRef;

public class CommandResponseDto {

    private final ActorRef leader;

    public CommandResponseDto(ActorRef leader) {
        this.leader = leader;
    }

    public ActorRef getLeader() {
        return leader;
    }
}
