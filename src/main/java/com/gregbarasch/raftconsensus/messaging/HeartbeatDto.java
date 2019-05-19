package com.gregbarasch.raftconsensus.messaging;

public class HeartbeatDto extends RaftMessage implements AppendEntriesRequestMessage {
    public HeartbeatDto(long term) {
        super(term);
    }
}
