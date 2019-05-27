package com.gregbarasch.raftconsensus.model;

import org.apache.log4j.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;

public class RaftStateMachine {

    private RaftStateMachine() {}

    public enum State {
        FOLLOWER, CANDIDATE, LEADER
    }

    // state machine for all servers
    public static class StateData {

        private static final Logger logger = Logger.getLogger(StateData.class);
        private static final String STATE_MACHINE_FOLDER_NAME = "state_machine";
        private static final String STATE_MACHINE_FILE_PATH = STATE_MACHINE_FOLDER_NAME + File.separator + "state_machine";

        public StateData() {}

        public void apply(LogEntry entry) {
            persistToDisk(entry.getCommand().getCommand());
        }

        // TODO move persist and load into a util class
        private void persistToDisk(String command) {
            // create folder
            //noinspection ResultOfMethodCallIgnored
            new File(STATE_MACHINE_FOLDER_NAME).mkdir();

            // create file and write to it
            try (PrintWriter out = new PrintWriter(new FileOutputStream(STATE_MACHINE_FILE_PATH, true))) {
                out.println(command);
            } catch (IOException e) {
                logger.error("Unable to write object to path: " + STATE_MACHINE_FILE_PATH, e);
            }
        }
    }
}
