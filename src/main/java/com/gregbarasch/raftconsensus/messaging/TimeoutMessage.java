package com.gregbarasch.raftconsensus.messaging;

// Actors should only be sending timeouts to themselves
public enum  TimeoutMessage {
    TIMEOUT
}
