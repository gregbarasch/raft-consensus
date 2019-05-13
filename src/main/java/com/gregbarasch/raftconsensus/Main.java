package com.gregbarasch.raftconsensus;

import com.gregbarasch.raftconsensus.actor.RaftActorManager;
import org.apache.log4j.Logger;

import java.util.Scanner;
import java.util.concurrent.TimeoutException;

public class Main {

    private static final Logger logger = Logger.getLogger(Main.class);

    public static void main(String[] args) {

        RaftActorManager.INSTANCE.start();

        try (final Scanner stdin = new Scanner(System.in)) {

            //noinspection StatementWithEmptyBody
            while (!stdin.nextLine().equals(""));
            RaftActorManager.INSTANCE.kill();

        } catch (InterruptedException | TimeoutException ex) {
            logger.error("There was an exception encountered while killing the system: ", ex);
            System.exit(-1);
        }

        logger.info("System Terminated");
    }
}
