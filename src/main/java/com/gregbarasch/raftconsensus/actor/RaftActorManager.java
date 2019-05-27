package com.gregbarasch.raftconsensus.actor;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Terminated;
import akka.pattern.Patterns;
import org.apache.log4j.Logger;
import scala.concurrent.Await;
import scala.concurrent.Future;

import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * This class handles the creation and destruction of our actor system + instances
 */
public enum RaftActorManager {

    INSTANCE;

    private static final Logger logger = Logger.getLogger(RaftActorManager.class);

    private static final int NUM_INSTANCES = 5;

    private static final ActorSystem actorSystem = ActorSystem.create();
    private static LinkedList<ActorRef> actors = new LinkedList<>();

    public List<ActorRef> getActors() {
        return actors;
    }

    public void start() {
        // Create the actors
        for (int i = 0; i < NUM_INSTANCES; i++) {
            final ActorRef raftActor = actorSystem.actorOf(RaftActor.props());
            actors.add(raftActor);
        }

        logger.info(NUM_INSTANCES + " RaftActors were generated.");
    }

    public void kill() throws InterruptedException, TimeoutException {
        final Duration timeout = Duration.ofSeconds(15);

        // stop the actors
        while (!actors.isEmpty()) {
            final ActorRef actor = actors.pop();
            Patterns.gracefulStop(actor, timeout).toCompletableFuture().join();
        }

        // stop the system
        final Future<Terminated> terminate = actorSystem.terminate();
        Await.ready(terminate, scala.concurrent.duration.Duration.fromNanos(timeout.toNanos()));
    }
}
