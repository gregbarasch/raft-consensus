package com.gregbarasch.raftconsensus;

import com.gregbarasch.raftconsensus.actor.RaftActorManager;

public class Main {

    public static void main(String[] args) {
        RaftActorManager.INSTANCE.start();
    }
}
