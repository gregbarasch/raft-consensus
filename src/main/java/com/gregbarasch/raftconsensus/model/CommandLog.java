package com.gregbarasch.raftconsensus.model;

import java.util.ArrayList;

public class CommandLog {
    private final ArrayList<LogEntry> log = new ArrayList<>();
    private int commitIndex = 0;
    private int lastApplied = 0;
}
