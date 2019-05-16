package com.gregbarasch.raftconsensus.messaging;

import com.gregbarasch.raftconsensus.model.LogEntry;

import java.util.List;

/**
 * FIXME
 * AppendEntries is used by the Leader node for replicating the log
 * entries and also as a heartbeat mechanism to check if a server
 * is still up. If heartbeat is responded back to, the server is up else,
 * the server is down. Be noted that the heartbeats do not contain any log entries.
 */

// SO the first suffix is empty, but we will get the last index
public class AppendEntriesRequestDto extends RaftMessage implements AppendEntriesRequestMessage {

    private final int commitIndex;
    private final int prevLogIndex;
    private final int prevLogTerm;
    private final List<LogEntry> logSuffix;

    public AppendEntriesRequestDto(long term, int latestCommitIndex, int prevLogIndex, int prevLogTerm, List<LogEntry> logSuffix) {
        super(term);
        this.commitIndex = latestCommitIndex;
        this.prevLogIndex = prevLogIndex;
        this.prevLogTerm = prevLogTerm;
        this.logSuffix = logSuffix;
    }

    public int getCommitIndex() {
        return commitIndex;
    }

    public int getPrevLogIndex() {
        return prevLogIndex;
    }

    public int getPrevLogTerm() {
        return prevLogTerm;
    }

    public List<LogEntry> getLogSuffix() {
        return logSuffix;
    }
}
