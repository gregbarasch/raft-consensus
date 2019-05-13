package com.gregbarasch.raftconsensus.actor;

import akka.actor.ActorRef;

class RaftStateMachine {

    private RaftStateMachine() {}

    enum State {
        FOLLOWER, CANDIDATE, LEADER
    }

    static class Data {

        private long term = 0;
        private ActorRef votedFor = null;

        void nextTerm() {
            term++;
            votedFor = null;
        }

        // Assumes that the term can only go forward. FIXME throw error?
        void newTerm(long term) {
            this.term = term;
            votedFor = null;
        }

        long getTerm() {
            return term;
        }

        void votedFor(ActorRef actor) {
            votedFor = actor;
        }

        ActorRef votedFor() {
            return votedFor;
        }
    }
}
