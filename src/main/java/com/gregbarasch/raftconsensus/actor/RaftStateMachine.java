package com.gregbarasch.raftconsensus.actor;

class RaftStateMachine {

    private RaftStateMachine() {}

    enum State {
        FOLLOWER, CANDIDATE, LEADER
    }

    static class Data {

        private long term = 0;
        private boolean voted = false;

        void nextTerm() {
            term++;
            voted = false;
        }

        // Assumes that the term can only go forward. FIXME throw error?
        void newTerm(long term) {
            this.term = term;
            voted = false;
        }

        long getTerm() {
            return term;
        }

        void voted() {
            voted = true;
        }

        boolean hasVoted() {
            return voted;
        }
    }
}
