package com.gregbarasch.raftconsensus.model;

import org.apache.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class RaftStateMachine {

    private RaftStateMachine() {}

    public enum State {
        FOLLOWER, CANDIDATE, LEADER
    }

    // state machine for all servers
    public static class StateData {

        private static final Logger logger = Logger.getLogger(StateData.class);
        private static final String STATE_MACHINE_FOLDER_NAME = "state_machine";

        private final int id;
        private int ticketCount = 0; // FIXME make parametric

        public StateData(int id) {
            this.id = id;
        }

        public void apply(Command command) {
            ticketCount += command.getAmount();
            persistToDisk();
        }

        public int getTicketCount() {
            return ticketCount;
        }

        // TODO move persist and load into a util class
        private void persistToDisk() {
            // create folder
            //noinspection ResultOfMethodCallIgnored
            new File(STATE_MACHINE_FOLDER_NAME).mkdir();

            // create file and write to it
            final String pathToFile = STATE_MACHINE_FOLDER_NAME + File.separator + id;
            try (final ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(pathToFile, false))) {
                oos.writeObject(this);
            } catch (IOException e) {
                logger.error("Unable to write object to path: " + pathToFile, e);
            }
        }

        private StateData loadFromDisk() {
            final String pathToFile = STATE_MACHINE_FOLDER_NAME + File.separator + id;
            try (final ObjectInputStream ois = new ObjectInputStream(new FileInputStream(pathToFile))) {
                return (StateData) ois.readObject();
            } catch (Exception e) {
                return null;
            }
        }
    }
}
