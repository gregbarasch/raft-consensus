package com.gregbarasch.raftconsensus.messaging;

public interface RaftMessage {
    long getTerm();
}
