package com.gregbarasch.raftconsensus.actor;

import akka.actor.AbstractFSM;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.pf.FSMStateFunctionBuilder;
import akka.japi.pf.FSMTransitionHandlerBuilder;
import com.gregbarasch.raftconsensus.messaging.*;
import com.gregbarasch.raftconsensus.model.RaftStateMachine;
import com.gregbarasch.raftconsensus.model.VolatileLeaderData;
import org.apache.log4j.Logger;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static com.gregbarasch.raftconsensus.model.RaftStateMachine.State.CANDIDATE;
import static com.gregbarasch.raftconsensus.model.RaftStateMachine.State.FOLLOWER;
import static com.gregbarasch.raftconsensus.model.RaftStateMachine.State.LEADER;

// TODO note in bugs that every actor has the same heartbeat timer
// TODO note how i dont have a hearrtbeat retry for ONLY people who didnt respond
// TODO note how I dont retry for a requestVote
// TODO there might be a race condition in between when a candidate becomes a leader from receiving marjority, and when the old leader discovers the new leader
// TODO I did not limit the batch size
// TODO discuss how I did not implement a ds to relate requests to responses... What, like a correlation ID?

// TODO follower rcan redirerct clients
// TODO persist stuff to the disk
// TODO actually use the commands...

/**
    After the entry is committed, the leader executes the entry and responds back with the result to the client.
    It should be noted that these entries are executed in the order they are received.
*/

// TODO followers only apply current term entries. only if suffix is compatible
// TODO followers only refuse an update if therers an earlier conflict : leader will send longer suffix next time
// TODO leader sends its last entry, followers might reject if the suffix is bad and follower will respond with their good 1

class RaftActor extends AbstractFSM<RaftStateMachine.State, RaftStateMachine.PersistentData> {
    private static final Logger logger = Logger.getLogger(RaftActor.class);

    // Used for elections
    private Set<ActorRef> actorsDidntVoteYesSet;

    private VolatileLeaderData leaderData = null;
    private int commitIndex = 0;
    private int lastApplied = 0;

    static Props props() {
        return Props.create(RaftActor.class);
    }

    private RaftActor() {
        when(FOLLOWER,
                new FSMStateFunctionBuilder<RaftStateMachine.State, RaftStateMachine.PersistentData>()
                        .event(Timeout.class, (timeout, data) -> {
                            resetTimeout(Timeout.ELECTION);
                            return goTo(CANDIDATE);
                        })
                        .event(AppendEntriesRequestDto.class, (message, data) -> {
                            resetTimeout(Timeout.ELECTION);
                            return onAppendEntriesRequestDto(message);
                        })
                        .event(VoteRequestDto.class, (request, data) -> onRequestVoteDto(request))
                        .build()
        );

        when(CANDIDATE,
                new FSMStateFunctionBuilder<RaftStateMachine.State, RaftStateMachine.PersistentData>()
                        .event(Timeout.class, (timeout, data) -> {
                            resetTimeout(Timeout.ELECTION);
                            return goTo(CANDIDATE);
                        })
                        .event(AppendEntriesRequestDto.class, (message, data) -> {
                            resetTimeout(Timeout.ELECTION); // FIXME??? i think this is correct
                            return onAppendEntriesRequestDto(message);
                        })
                        .event(VoteRequestDto.class, (request, data) -> onRequestVoteDto(request))
                        .event(VoteResponseDto.class, (vote, data) -> onVoteDto(vote))
                        .build()
        );

        when(LEADER,
                new FSMStateFunctionBuilder<RaftStateMachine.State, RaftStateMachine.PersistentData>()
                        .event(Timeout.class, (timeout, data) -> {
                            resetTimeout(Timeout.HEARTBEAT);
                            appendEntries();
                            return stay();
                        })
                        .event(AppendEntriesRequestDto.class, (message, data) -> onAppendEntriesRequestDto(message))
                        .event(AppendEntriesResponseDto.class, (response, data) -> onAppendEntriesResponseDto(response))
                        .event(VoteRequestDto.class, (request, data) -> onRequestVoteDto(request))
                        .build()
        );

        whenUnhandled(new FSMStateFunctionBuilder<RaftStateMachine.State, RaftStateMachine.PersistentData>()
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
                        .state(CANDIDATE, LEADER, this::handleLeaderTransition)
                        .state(LEADER, FOLLOWER, () -> {
                            cancelTimeout(Timeout.HEARTBEAT);
                            startTimeout(Timeout.ELECTION);
                            logger.info(getSelf().hashCode() + " is no longer the leader");
                        })
                        .build()
        );

        startWith(FOLLOWER, new RaftStateMachine.PersistentData());
        startTimeout(Timeout.ELECTION);
    }

    private void handleLeaderTransition() {
        // reset leaderData
        final com.gregbarasch.raftconsensus.model.LogEntry lastLogEntry = stateData().getLog().getLastEntry();
        if (lastLogEntry == null) {
            leaderData = new VolatileLeaderData(-1);
        } else {
            leaderData = new VolatileLeaderData(lastLogEntry.getIndex());
        }

        // Register self with system as leader
        RaftActorManager.INSTANCE.setLeader(getSelf());
        logger.info(getSelf().hashCode() + " has become the leader");

        // kick off heartbeat
        cancelTimeout(Timeout.ELECTION);
        startTimeout(Timeout.HEARTBEAT);
        appendEntries();
    }

    /**
     * Sending in batches
     */
    private void appendEntries() {
        // For each actor
        for (final ActorRef actor : RaftActorManager.INSTANCE.getActors()) {
            if (actor.equals(getSelf())) continue; // skip self

            final int actorNextIndex = leaderData.getNextIndex(actor);
            final int actorMatchIndex = leaderData.getMatchIndex(actor);

            // if were within bounds
            if (actorNextIndex <= stateData().getLog().size()) {

                // calculate the end index
                int endIndex = stateData().getLog().size()-1;
                if (actorMatchIndex + 1 < actorNextIndex || endIndex < actorNextIndex) {
                    endIndex = actorNextIndex;
                }

                int prevIndex = actorNextIndex-1;
                long prevTerm = (prevIndex <= 0) ? 0 : stateData().getLog().getEntry(prevIndex).getTerm();

                final List<com.gregbarasch.raftconsensus.model.LogEntry> logEntries = stateData().getLog().subLog(actorNextIndex, endIndex);
                final AppendEntriesRequestDto request = new AppendEntriesRequestDto(
                        stateData().getTerm(),
                        Math.min(endIndex, commitIndex),
                        prevIndex,
                        prevTerm,
                        logEntries);

                actor.tell(request, getSelf());
            }
        }

        // If an actor is skipped, it means that its log is ahead of ours and it should become the leader
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
        for (final ActorRef actor : actorsDidntVoteYesSet) {
            if (actor.equals(getSelf())) continue; // skip self
            actor.tell(voteRequestDto, getSelf()); // TODO request vote again with a timer orr something
        }
    }

    private State<RaftStateMachine.State, RaftStateMachine.PersistentData> onRequestVoteDto(VoteRequestDto voteRequestDto) {

        final State<RaftStateMachine.State, RaftStateMachine.PersistentData> nextState = syncTerm(voteRequestDto);

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

    private State<RaftStateMachine.State, RaftStateMachine.PersistentData> onVoteDto(VoteResponseDto voteResponseDto) {

        State<RaftStateMachine.State, RaftStateMachine.PersistentData> nextState = syncTerm(voteResponseDto);

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

    private State<RaftStateMachine.State, RaftStateMachine.PersistentData> onAppendEntriesRequestDto(AppendEntriesRequestDto request) {

        final State<RaftStateMachine.State, RaftStateMachine.PersistentData> nextState = syncTerm(request);

        // Return false
        boolean success = true;
        if (request.getTerm() < stateData().getTerm()) {
            success = false;
        } else {

        }

        // TODO append the entry...

        final AppendEntriesResponseDto response = new AppendEntriesResponseDto(stateData().getTerm(), success);
        getSender().tell(response, getSelf());
        return nextState;
    }

    private State<RaftStateMachine.State, RaftStateMachine.PersistentData> onAppendEntriesResponseDto(AppendEntriesResponseDto response) {

        // Here we want to fail fast. Return instantly, don't proceed
        final State<RaftStateMachine.State, RaftStateMachine.PersistentData> nextState = syncTerm(response);
        if (nextState.stateName() != stateName()) return nextState;

        // TODO dostuff

        return nextState;
    }

    private State<RaftStateMachine.State, RaftStateMachine.PersistentData> syncTerm(RaftMessage message) {
        // check if we are out of sync
        final long sendersTerm = message.getTerm();
        if (sendersTerm > stateData().getTerm()) {
            stateData().newTerm(sendersTerm);

            // fail to follower if were not already
            if (!(stateName() == FOLLOWER)) return goTo(FOLLOWER);
        }
        return stay();
    }

    private void startTimeout(Timeout timeout) {
        // heartbeats happen twice as often as election timeouts arbitrarily
        int min = 150;
        int max = 300;
        if (timeout == Timeout.HEARTBEAT) {
            min =  min / 2;
            max = max / 2;
        }

        final long timeoutMillis = new Random().nextInt(max - min + 1) + min;
        setTimer(timeout.name(), timeout, Duration.ofMillis(timeoutMillis));
    }

    private void cancelTimeout(Timeout timeout) {
        cancelTimer(timeout.name());
    }

    private void resetTimeout(Timeout timeout) {
        cancelTimeout(timeout);
        startTimeout(timeout);
    }

    private enum Timeout {
        ELECTION, HEARTBEAT
    }
}
