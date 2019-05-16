package com.gregbarasch.raftconsensus.messaging;

public class HeartbeatDto extends RaftMessage implements AppendEntriesRequestMessage{
    HeartbeatDto(long term) {
        super(term);
    }
}
