package com.gregbarasch.raftconsensus.model;

import akka.actor.ActorRef;

import java.util.HashMap;

public class LeaderData {
    // for each server, index of next log entry to send to that server (initialized to leader last log index + 1)
    private final HashMap<ActorRef, Integer> nextIndex = new HashMap<>();

    // for each server, index of highest log entry known to be replicated on server (initialized to 0, increases monotonically)
    private final HashMap<ActorRef, Integer> matchIndex = new HashMap<>();

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
        matchIndex.put(actor, index);
    }
}
