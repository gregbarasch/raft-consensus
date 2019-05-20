package com.gregbarasch.raftconsensus.model;

import akka.actor.ActorRef;
import com.gregbarasch.raftconsensus.actor.RaftActorManager;

import java.util.HashMap;

/**
 * This data will be reinitialized per actor post-election
 */
public class VolatileLeaderData {
    // for each server, index of next log entry to send to that server (initialized to leader last log index + 1)
    private final HashMap<ActorRef, Integer> nextIndex;

    // for each server, index of highest log entry known to be replicated on server (initialized to 0, increases monotonically)
    private final HashMap<ActorRef, Integer> matchIndex;

    // FIXME ?? fix indexs
    public VolatileLeaderData(int lastLogIndex) {
        nextIndex = new HashMap<>();
        matchIndex = new HashMap<>();

        for (final ActorRef actor : RaftActorManager.INSTANCE.getActors()) {
            nextIndex.put(actor, lastLogIndex+1);
            matchIndex.put(actor, 0);
        }
    }

    public int getNextIndex(ActorRef actor) {
        return nextIndex.get(actor);
    }

    public int getMatchIndex(ActorRef actor) {
        return matchIndex.get(actor);
    }

    public void setNextIndex(ActorRef actor, int index) {
        nextIndex.put(actor, index);
    }

    public void setMatchIndex(ActorRef actor, int index) {
        // TODO increases monotonically
        matchIndex.put(actor, index);
    }
}
