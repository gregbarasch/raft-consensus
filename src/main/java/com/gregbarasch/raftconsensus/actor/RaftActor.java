package com.gregbarasch.raftconsensus.actor;

import akka.actor.AbstractFSM;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.pf.FSMStateFunctionBuilder;
import akka.japi.pf.FSMTransitionHandlerBuilder;
import com.gregbarasch.raftconsensus.messaging.*;
import com.gregbarasch.raftconsensus.model.RaftStateMachine;
import org.apache.log4j.Logger;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static com.gregbarasch.raftconsensus.model.RaftStateMachine.State.CANDIDATE;
import static com.gregbarasch.raftconsensus.model.RaftStateMachine.State.FOLLOWER;
import static com.gregbarasch.raftconsensus.model.RaftStateMachine.State.LEADER;

// TODO note in bugs that i share teh same timeout for heartbeating and for regular timeouts
// TODO note how i dont have a hearrtbeat retry for ONLY people who didnt respond
// TODO note how I dont retry for a requestVote

// TODO persist stuff to the disk

/**
    After the entry is committed, the leader executes the entry and responds back with the result to the client.
    It should be noted that these entries are executed in the order they are received.
*/

// TODO followers only apply current term entries. only if suffix is compatible
// TODO followers only refuse an update if therers an earlierr conflict : leader will send longer suffix next time
// TODO leader sends its last entry, followers might reject if the suffix is bad and follower will respond with their good 1

class RaftActor extends AbstractFSM<RaftStateMachine.State, RaftStateMachine.Data> {
    private static final Logger logger = Logger.getLogger(RaftActor.class);

    // Used for elections
    private Set<ActorRef> actorsDidntVoteYesSet;
    // Used for heartbeating
    private Set<ActorRef> actorsDidntBeatSet;

    static Props props() {
        return Props.create(RaftActor.class);
    }

    private RaftActor() {
        when(FOLLOWER,
                new FSMStateFunctionBuilder<RaftStateMachine.State, RaftStateMachine.Data>()
                        .event(Timeout.class, (timeout, data) -> {
                            resetTimeout();
                            return goTo(CANDIDATE);
                        })
                        .event(AppendEntriesRequestMessage.class, (message, data) -> {
                            resetTimeout();
                            return onAppendEntriesRequestMessage(message);
                        })
                        .event(VoteRequestDto.class, (request, data) -> onRequestVoteDto(request))
                        .build()
        );

        when(CANDIDATE,
                new FSMStateFunctionBuilder<RaftStateMachine.State, RaftStateMachine.Data>()
                        .event(Timeout.class, (timeout, data) -> {
                            resetTimeout();
                            return goTo(CANDIDATE);
                        })
                        .event(AppendEntriesRequestMessage.class, (message, data) -> {
                            resetTimeout(); // FIXME??? i think this is correct
                            return onAppendEntriesRequestMessage(message);
                        })
                        .event(VoteRequestDto.class, (request, data) -> onRequestVoteDto(request))
                        .event(VoteResponseDto.class, (vote, data) -> onVoteDto(vote))
                        .build()
        );

        when(LEADER,
                new FSMStateFunctionBuilder<RaftStateMachine.State, RaftStateMachine.Data>()
                        .event(Timeout.class, (timeout, data) -> {
                            resetTimeout();
                            appendEntries();
                            return stay();
                        })
                        .event(AppendEntriesResponseDto.class, (response, data) -> onAppendEntriesResponseDto(response))
                        .event(VoteRequestDto.class, (request, data) -> onRequestVoteDto(request))
                        .build()
        );

        whenUnhandled(new FSMStateFunctionBuilder<RaftStateMachine.State, RaftStateMachine.Data>()
                .anyEvent((event, data) -> {
                    logger.warn(getSelf().hashCode() + " unhandled event: " + event.getClass() + " in state: " + stateName().name());
                    return stay();
                })
                .build()
        );

        onTransition(
                new FSMTransitionHandlerBuilder<RaftStateMachine.State>()
                        .state(FOLLOWER, CANDIDATE, this::election)
                        .state(CANDIDATE, CANDIDATE, this::election)
                        .state(CANDIDATE, FOLLOWER, () -> {})
                        .state(CANDIDATE, LEADER, () -> {
                            logger.info(getSelf().hashCode() + " has become the leader");
                            resetTimeout();
                            // TODO new LeaderData
                            appendEntries();
                        })
                        .state(LEADER, FOLLOWER, () -> logger.info(getSelf().hashCode() + " is no longer the leader"))
                        .build()
        );

        startWith(FOLLOWER, new RaftStateMachine.Data());
        startTimeout();
    }

    private void appendEntries() {
        // (FIXME make it work for more than just heartbeat)
        actorsDidntBeatSet = new HashSet<>(RaftActorManager.INSTANCE.getActors()); // FIXME try again for pepes who didnt beat only?
        actorsDidntBeatSet.remove(getSelf());

        final AppendEntriesRequestMessage request = new HeartbeatDto(stateData().getTerm());
        for (final ActorRef actor : new ArrayList<>(actorsDidntBeatSet)) {
            if (actor.equals(getSelf())) continue; // skip self
            actor.tell(request, getSelf());
        }
    }

    private void election() {
        stateData().nextTerm();
        logger.info(getSelf().hashCode() + " has started an election in term " + stateData().getTerm());

        // vote for self
        actorsDidntVoteYesSet = new HashSet<>(RaftActorManager.INSTANCE.getActors());
        actorsDidntVoteYesSet.remove(getSelf());
        stateData().votedFor(getSelf());

        // get lastLogEntry related info
        final com.gregbarasch.raftconsensus.model.LogEntry lastLogEntry = stateData().getLog().getLastEntry();

        Integer lastLogIndex = null;
        Long lastLogTerm = null;
        if (lastLogEntry != null) {
            lastLogIndex = lastLogEntry.getIndex();
            lastLogTerm = lastLogEntry.getTerm();
        }

        // Attempt to receive votes from majority of actors
        final VoteRequestDto voteRequestDto = new VoteRequestDto(stateData().getTerm(), lastLogIndex, lastLogTerm);
        for (final ActorRef actor : new ArrayList<>(actorsDidntVoteYesSet)) {
            if (actor.equals(getSelf())) continue; // skip self
            actor.tell(voteRequestDto, getSelf()); // TODO request vote again with a timer orr something
        }
    }

    private State<RaftStateMachine.State, RaftStateMachine.Data> onRequestVoteDto(VoteRequestDto voteRequestDto) {

        final State<RaftStateMachine.State, RaftStateMachine.Data> nextState = syncTerm(voteRequestDto);

        // Only vote yes once per term
        boolean vote = false;
        if (stateData().votedFor() == null) {

            final com.gregbarasch.raftconsensus.model.LogEntry lastLogEntry = stateData().getLog().getLastEntry();
            final Integer requestLogIndex = voteRequestDto.getLogIndex();
            final Long requestLogTerm = voteRequestDto.getLogTerm();

            // If everything is unset, we can grant a vote
            if ((lastLogEntry == null && requestLogIndex == null && requestLogTerm == null)) {
                stateData().votedFor(getSender());
                vote = true;
            } else {
                // Otherwise, only vote for nodes whose logs are up to date
                if (lastLogEntry != null
                        && requestLogIndex.compareTo(lastLogEntry.getIndex()) >= 0
                        && requestLogTerm.compareTo(lastLogEntry.getTerm()) >= 0) {
                    stateData().votedFor(getSender());
                    vote = true;
                }
            }
        }

        // send response
        VoteResponseDto voteResponseDto = new VoteResponseDto(stateData().getTerm(), vote);
        getSender().tell(voteResponseDto, getSelf());

        return nextState;
    }

    private State<RaftStateMachine.State, RaftStateMachine.Data> onVoteDto(VoteResponseDto voteResponseDto) {

        State<RaftStateMachine.State, RaftStateMachine.Data> nextState = syncTerm(voteResponseDto);

        // Remove
        if (voteResponseDto.isYes()) {
            actorsDidntVoteYesSet.remove(getSender());
        }

        // If we have enough votes, become the leader
        if (actorsDidntVoteYesSet.size() <= (RaftActorManager.INSTANCE.getActors().size()-1)/2) {
            nextState = goTo(LEADER); // Should only receive vote when our term is newest
        }

        return nextState;
    }

    private State<RaftStateMachine.State, RaftStateMachine.Data> onAppendEntriesRequestMessage(AppendEntriesRequestMessage message) {

        final RaftMessage raftMessage = (RaftMessage) message;
        final State<RaftStateMachine.State, RaftStateMachine.Data> nextState = syncTerm(raftMessage);

        // FIXME
        final AppendEntriesResponseDto response = new AppendEntriesResponseDto(stateData().getTerm(), true);
        getSender().tell(response, getSelf());

        return nextState;
    }

    private State<RaftStateMachine.State, RaftStateMachine.Data> onAppendEntriesResponseDto(AppendEntriesResponseDto response) {

        // Here we want to fail fast. Return instantly, don't proceed
        final State<RaftStateMachine.State, RaftStateMachine.Data> nextState = syncTerm(response);
        if (nextState.stateName() != stateName()) return nextState;

        actorsDidntBeatSet.remove(getSender());

        // If we have enough votes, become the leader
        if (actorsDidntBeatSet.size() <= (RaftActorManager.INSTANCE.getActors().size()-1)/2) {
            resetTimeout();
            appendEntries();
        }

        return nextState;
    }

    private State<RaftStateMachine.State, RaftStateMachine.Data> syncTerm(RaftMessage message) {
        // check if we are out of sync
        final long sendersTerm = message.getTerm();
        if (sendersTerm > stateData().getTerm()) {
            stateData().newTerm(sendersTerm);

            // fail to follower if were not already
            if (!(stateName() == FOLLOWER)) return goTo(FOLLOWER);
        }
        return stay();
    }

    private void startTimeout() {
        final int min = 150;
        final int max = 300;
        final long timeoutMillis = new Random().nextInt(max - min + 1) + min;
        setTimer(Timeout.INSTANCE.name(), Timeout.INSTANCE, Duration.ofMillis(timeoutMillis));
    }

    private void cancelTimeout() {
        cancelTimer(Timeout.INSTANCE.name());
    }

    private void resetTimeout() {
        cancelTimeout();
        startTimeout();
    }

    // TODO ELECTION, HEARTBEAT
    private enum Timeout {
        INSTANCE
    }
}
