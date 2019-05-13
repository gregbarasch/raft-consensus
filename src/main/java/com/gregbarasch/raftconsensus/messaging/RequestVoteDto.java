package com.gregbarasch.raftconsensus.messaging;

public class RequestVoteDto extends RaftMessage {

    public RequestVoteDto(long term) {
        super(term);
    }
}
