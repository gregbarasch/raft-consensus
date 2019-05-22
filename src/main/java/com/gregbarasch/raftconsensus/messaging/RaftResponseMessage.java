package com.gregbarasch.raftconsensus.messaging;

// This avoids term confusion
public interface RaftResponseMessage extends RaftMessage {
    long getOriginalRequestTerm();
}
