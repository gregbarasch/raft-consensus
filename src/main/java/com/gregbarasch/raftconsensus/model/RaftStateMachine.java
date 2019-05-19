package com.gregbarasch.raftconsensus.model;

import akka.actor.ActorRef;

public class RaftStateMachine {

    private RaftStateMachine() {}

    public enum State {
        FOLLOWER, CANDIDATE, LEADER
    }

    // Persistent data for all servers
    public static class PersistentData {

        private long term = 0;
        private final Log log = new Log();
        private ActorRef votedFor = null;

        public void nextTerm() {
            term++;
            votedFor = null;
        }

        // The term can only go forward
        public void newTerm(long term) {
            if (term <= this.term) throw new RuntimeException("Invalid term");
            this.term = term;
            votedFor = null;
        }

        public long getTerm() {
            return term;
        }

        public Log getLog() {
            return log;
        }

        public void votedFor(ActorRef actor) {
            votedFor = actor;
        }

        public ActorRef votedFor() {
            return votedFor;
        }

        // TODO
        public void persistToDisk() {}
        public void loadFromDisk() {}
    }
}
