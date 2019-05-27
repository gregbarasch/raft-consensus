package com.gregbarasch.raftconsensus.model.statemachines;

import akka.actor.ActorRef;
import com.gregbarasch.raftconsensus.actor.RaftActorManager;
import com.gregbarasch.raftconsensus.model.Log;
import org.apache.log4j.Logger;

import java.io.*;
import java.util.HashMap;

public class ActorStateMachine {

    private ActorStateMachine() {}

    public enum State {
        FOLLOWER, CANDIDATE, LEADER
    }

    // state machine for all servers
    public static class StateData {

        private PersistentActorData persistentActorData;
        private VolatileLeaderData volatileLeaderData;

        public StateData(int id) {
            persistentActorData = new PersistentActorData(id);
        }

        public void newVolatileLeaderData(int nextIndex) {
            volatileLeaderData = new VolatileLeaderData(nextIndex);
        }

        public PersistentActorData getPersistentActorData() {
            return persistentActorData;
        }

        public VolatileLeaderData getVolatileLeaderData() {
            return volatileLeaderData;
        }
    }

    /**
     * Will be persisted to disk. Exists for all actors
     */
    public static class PersistentActorData implements Serializable {

        private static final Logger logger = Logger.getLogger(PersistentActorData.class);
        private static final long serialVersionUID = 1L;
        private static final String PERSIST_FOLDER_NAME = "persistent_data";

        private final int id;
        private long term;
        private final Log log;
        private ActorRef votedFor;

        PersistentActorData(int id) {
            this.id = id;

            PersistentActorData diskData = loadFromDisk();
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

        private PersistentActorData loadFromDisk() {
            final String pathToFile = PERSIST_FOLDER_NAME + File.separator + id;
            try (final ObjectInputStream ois = new ObjectInputStream(new FileInputStream(pathToFile))) {
                return (PersistentActorData) ois.readObject();
            } catch (Exception e) {
                return null;
            }
        }
    }

    /**
     * This data will be reinitialized per actor post-election
     */
    public static class VolatileLeaderData {
        // for each server, index of next log entry to send to that server (initialized to leader last log index + 1)
        private final HashMap<ActorRef, Integer> nextIndex;

        // for each server, index of highest log entry known to be replicated on server (initialized to 0, increases monotonically)
        private final HashMap<ActorRef, Integer> matchIndex;

        VolatileLeaderData(int lastLogIndex) {
            nextIndex = new HashMap<>();
            matchIndex = new HashMap<>();

            for (final ActorRef actor : RaftActorManager.INSTANCE.getActors()) {
                nextIndex.put(actor, lastLogIndex+1);
                matchIndex.put(actor, -1);
            }
        }

        public int getNextIndex(ActorRef actor) {
            return nextIndex.get(actor);
        }

        public int getMatchIndex(ActorRef actor) {
            return matchIndex.get(actor);
        }

        public void setNextIndex(ActorRef actor, int index) {
            nextIndex.put(actor, index);
        }

        public void setMatchIndex(ActorRef actor, int index) {
            // TODO increases monotonically else error
            matchIndex.put(actor, index);
        }
    }
}
