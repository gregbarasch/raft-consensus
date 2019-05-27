package com.gregbarasch.raftconsensus;

import akka.actor.ActorRef;
import akka.pattern.Patterns;
import com.gregbarasch.raftconsensus.actor.RaftActorManager;
import com.gregbarasch.raftconsensus.messaging.CommandRequestDto;
import com.gregbarasch.raftconsensus.messaging.CommandResponseDto;
import org.apache.log4j.Logger;

import java.time.Duration;
import java.util.Scanner;
import java.util.concurrent.TimeoutException;

public class Main {

    private static final Logger logger = Logger.getLogger(Main.class);

    public static void main(String[] args) {

        // start and get a random instance. If its not the leader we'll be redirected
        RaftActorManager.INSTANCE.start();
        ActorRef leader = RaftActorManager.INSTANCE.getRandomActor();

        try (final Scanner stdin = new Scanner(System.in)) {

            // enter command string
            String input = stdin.nextLine();
            while (!input.equals("")) {

                // request the commmand from actor
                CommandRequestDto request = new CommandRequestDto(input, "ID"); // FIXME id
                CommandResponseDto response = (CommandResponseDto) Patterns.ask(leader, request, Duration.ofMillis(500)).toCompletableFuture().join();

                // wait till we hit the correct leader
                while (response.getLeader() != leader) {
                    leader = response.getLeader();
                    response = (CommandResponseDto) Patterns.ask(leader, request, Duration.ofMillis(500)).toCompletableFuture().join();
                }

                // get next input
                input = stdin.nextLine(); // FIXME times out and throws exception
            }

            // Kill instance
            RaftActorManager.INSTANCE.kill();

        } catch (InterruptedException | TimeoutException ex) {
            logger.error("There was an exception encountered while killing the system: ", ex);
            System.exit(-1);
        }

        logger.info("System Terminated");
    }
}
