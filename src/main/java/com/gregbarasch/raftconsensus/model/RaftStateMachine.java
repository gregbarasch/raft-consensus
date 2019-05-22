package com.gregbarasch.raftconsensus.model;

import akka.actor.ActorRef;
import org.apache.log4j.Logger;

import java.io.*;

public class RaftStateMachine {

    private RaftStateMachine() {}

    public enum State {
        FOLLOWER, CANDIDATE, LEADER
    }

    // Persistent data for all servers
    public static class PersistentData implements Serializable {

        private static final Logger logger = Logger.getLogger(PersistentData.class);

        private static final long serialVersionUID = 1L;
        private static final String PERSIST_FOLDER_NAME = "persist";

        private final int id;
        private long term;
        private final Log log;
        private ActorRef votedFor;

        public PersistentData(int id) {
            this.id = id;

            PersistentData diskData = loadFromDisk();
            if (diskData == null) {
                term = 0;
                log = new Log();
                votedFor = null;
            } else {
                term = diskData.getTerm();
                log = diskData.getLog();
                votedFor = diskData.votedFor();
            }
        }

        public void nextTerm() {
            term++;
            votedFor = null;
        }

        // The term can only go forward
        public void newTerm(long term) {
            if (term <= this.term) throw new RuntimeException("Invalid term");
            this.term = term;
            votedFor = null;
        }

        public long getTerm() {
            return term;
        }

        public Log getLog() {
            return log;
        }

        public void votedFor(ActorRef actor) {
            votedFor = actor;
        }

        public ActorRef votedFor() {
            return votedFor;
        }

        public void persistToDisk() {
            // create folder
            //noinspection ResultOfMethodCallIgnored
            new File(PERSIST_FOLDER_NAME).mkdir();

            // create file and write to it
            final String pathToFile = PERSIST_FOLDER_NAME + File.separator + id;
            try (final ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(pathToFile, false))) {
                oos.writeObject(this);
            } catch (IOException e) {
                logger.error("Unable to write object to path: " + pathToFile, e);
            }
        }

        private PersistentData loadFromDisk() {
            final String pathToFile = PERSIST_FOLDER_NAME + File.separator + id;
            try (final ObjectInputStream ois = new ObjectInputStream(new FileInputStream(pathToFile))) {
                return (PersistentData) ois.readObject();
            } catch (Exception e) {
                return null;
            }
        }
    }
}
