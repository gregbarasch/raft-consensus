package com.gregbarasch.raftconsensus.messaging;

public class RequestVoteDto extends RaftMessage {
    // FIXME vote only granted to nodes with more up to date logs...
    public RequestVoteDto(long term) {
        super(term);
    }
}
