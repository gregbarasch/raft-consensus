package com.gregbarasch.raftconsensus.actor;

import akka.actor.AbstractFSM;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.pf.FSMStateFunctionBuilder;
import akka.japi.pf.FSMTransitionHandlerBuilder;
import com.gregbarasch.raftconsensus.messaging.AppendEntriesRequestDto;
import com.gregbarasch.raftconsensus.messaging.AppendEntriesResponseDto;
import com.gregbarasch.raftconsensus.messaging.CommandRequestDto;
import com.gregbarasch.raftconsensus.messaging.RaftMessage;
import com.gregbarasch.raftconsensus.messaging.VoteRequestDto;
import com.gregbarasch.raftconsensus.messaging.VoteResponseDto;
import com.gregbarasch.raftconsensus.model.Log;
import com.gregbarasch.raftconsensus.model.RaftStateMachine;
import com.gregbarasch.raftconsensus.model.VolatileLeaderData;
import org.apache.log4j.Logger;

import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

class RaftActor extends AbstractFSM<RaftStateMachine.State, RaftStateMachine.PersistentData> {
    private static final Logger logger = Logger.getLogger(RaftActor.class);

    // Used for elections
    private Set<ActorRef> actorsDidntVoteYesSet;

    private VolatileLeaderData leaderData = null;
    private int commitIndex = -1;

    static Props props() {
        return Props.create(RaftActor.class);
    }

    private RaftActor() {
        when(RaftStateMachine.State.FOLLOWER,
                new FSMStateFunctionBuilder<RaftStateMachine.State, RaftStateMachine.PersistentData>()
                        .event(Timeout.class, (timeout, data) -> {
                            resetTimeout(Timeout.ELECTION);
                            return goTo(RaftStateMachine.State.CANDIDATE);
                        })
                        .event(AppendEntriesRequestDto.class, (message, data) -> {
                            // only process if the current leader sent message
                            if (getSender().equals(RaftActorManager.INSTANCE.getLeader())) {
                                resetTimeout(Timeout.ELECTION);
                                return onAppendEntriesRequestDto(message);
                            }
                            return stay();
                        })
                        .event(VoteRequestDto.class, (request, data) -> onVoteRequestDto(request))
                        .build()
        );

        when(RaftStateMachine.State.CANDIDATE,
                new FSMStateFunctionBuilder<RaftStateMachine.State, RaftStateMachine.PersistentData>()
                        .event(Timeout.class, (timeout, data) -> {
                            resetTimeout(Timeout.ELECTION);
                            return goTo(RaftStateMachine.State.CANDIDATE);
                        })
                        .event(AppendEntriesRequestDto.class, (message, data) -> {
                            resetTimeout(Timeout.ELECTION);
                            return onAppendEntriesRequestDto(message);
                        })
                        .event(VoteRequestDto.class, (request, data) -> onVoteRequestDto(request))
                        .event(VoteResponseDto.class, (vote, data) -> onVoteResponseDto(vote))
                        .build()
        );

        when(RaftStateMachine.State.LEADER,
                new FSMStateFunctionBuilder<RaftStateMachine.State, RaftStateMachine.PersistentData>()
                        .event(Timeout.class, (timeout, data) -> {
                            resetTimeout(Timeout.HEARTBEAT);
                            appendEntries();
                            return stay();
                        })
                        .event(AppendEntriesRequestDto.class, (message, data) -> onAppendEntriesRequestDto(message))
                        .event(AppendEntriesResponseDto.class, (response, data) -> onAppendEntriesResponseDto(response))
                        .event(VoteRequestDto.class, (request, data) -> onVoteRequestDto(request))
                        .event(CommandRequestDto.class, (command, data) -> {
                            logger.info(getSelf().hashCode() + " received command: " + command.getCommand().toString());
                            final com.gregbarasch.raftconsensus.model.LogEntry logEntry = new com.gregbarasch.raftconsensus.model.LogEntry(command, stateData().getLog().size(), stateData().getTerm());
                            stateData().getLog().putEntries(Collections.singletonList(logEntry));
                            return stay();
                        })
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
                        .state(RaftStateMachine.State.FOLLOWER, RaftStateMachine.State.CANDIDATE, this::election)
                        .state(RaftStateMachine.State.CANDIDATE, RaftStateMachine.State.CANDIDATE, this::election)
                        .state(RaftStateMachine.State.CANDIDATE, RaftStateMachine.State.FOLLOWER, () -> {})
                        .state(RaftStateMachine.State.CANDIDATE, RaftStateMachine.State.LEADER, this::handleLeaderTransition)
                        .state(RaftStateMachine.State.LEADER, RaftStateMachine.State.FOLLOWER, () -> {
                            cancelTimeout(Timeout.HEARTBEAT);
                            startTimeout(Timeout.ELECTION);
                            logger.info(getSelf().hashCode() + " is no longer the leader");
                        })
                        .build()
        );

        startWith(RaftStateMachine.State.FOLLOWER, new RaftStateMachine.PersistentData(getId()));
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

    private void appendEntries() {
        // For each actor
        for (final ActorRef actor : RaftActorManager.INSTANCE.getActors()) {
            if (actor.equals(getSelf())) continue; // skip self

            // if were within bounds
            final int actorNextIndex = leaderData.getNextIndex(actor);
            if (actorNextIndex <= stateData().getLog().size()) {

                // set some values
                final int toIndex = Math.min(stateData().getLog().size(), actorNextIndex+1); // For now we should only be sending 1 entry
                final int prevIndex = actorNextIndex-1;
                long prevTerm = getTermFromLog(prevIndex);

                // construct request
                final List<com.gregbarasch.raftconsensus.model.LogEntry> logEntries = stateData().getLog().subLog(actorNextIndex, toIndex);
                final AppendEntriesRequestDto request = new AppendEntriesRequestDto(
                        stateData().getTerm(),
                        Math.min(toIndex-1, commitIndex),
                        prevIndex,
                        prevTerm,
                        logEntries);

                // send request
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

        // Send vote request to all servers
        final VoteRequestDto voteRequestDto = new VoteRequestDto(stateData().getTerm(), lastLogIndex, lastLogTerm);
        for (final ActorRef actor : actorsDidntVoteYesSet) {
            actor.tell(voteRequestDto, getSelf()); // TODO request vote again with a timer for actors not received?
        }
    }

    private State<RaftStateMachine.State, RaftStateMachine.PersistentData> onVoteRequestDto(VoteRequestDto voteRequestDto) {

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

    private State<RaftStateMachine.State, RaftStateMachine.PersistentData> onVoteResponseDto(VoteResponseDto voteResponseDto) {

        State<RaftStateMachine.State, RaftStateMachine.PersistentData> nextState = syncTerm(voteResponseDto);

        // Remove
        if (voteResponseDto.isYes()) {
            actorsDidntVoteYesSet.remove(getSender());
        }

        // If we have enough votes, become the leader
        if (actorsDidntVoteYesSet.size() < (RaftActorManager.INSTANCE.getActors().size() / 2.0)) {
            nextState = goTo(RaftStateMachine.State.LEADER); // Should only receive vote when our term is newest
        }

        return nextState;
    }

    private State<RaftStateMachine.State, RaftStateMachine.PersistentData> onAppendEntriesRequestDto(AppendEntriesRequestDto request) {

        final State<RaftStateMachine.State, RaftStateMachine.PersistentData> nextState = syncTerm(request);
        boolean success = false;
        int matchIndex = 0;

        final long prevTerm = getTermFromLog(request.getPrevLogIndex());
        if (request.getTerm() >= stateData().getTerm()) {

            if (request.getPrevLogIndex() == -1
                || (request.getPrevLogIndex() < stateData().getLog().size()
                    && prevTerm == request.getPrevLogTerm())) {

                // append entries on success
                if (request.getEntries().size() > 0) {
                    stateData().getLog().putEntries(request.getEntries());
                }

                success = true;
                matchIndex = stateData().getLog().size() - 1;

                // update commit
                final int prevCommitIndex = commitIndex;
                commitIndex = Math.max(commitIndex, request.getCommitIndex()); // sender should have enough info to ensure that commitIndex sent is <= receivers log.size()-1
                if (prevCommitIndex != commitIndex) {
                    logger.debug(getSelf().hashCode() + " commitIndex set to: " + commitIndex);
                }
            }
        }

        // send response
        final AppendEntriesResponseDto response = new AppendEntriesResponseDto(stateData().getTerm(), success, matchIndex);
        getSender().tell(response, getSelf());
        return nextState;
    }

    private State<RaftStateMachine.State, RaftStateMachine.PersistentData> onAppendEntriesResponseDto(AppendEntriesResponseDto response) {

        final State<RaftStateMachine.State, RaftStateMachine.PersistentData> nextState = syncTerm(response);

        if (stateData().getTerm() == response.getTerm()) {
            if (response.isSuccess()) {
                leaderData.setMatchIndex(getSender(), response.getMatchIndex());
                leaderData.setNextIndex(getSender(), response.getMatchIndex()+1);

                // only commits 1 at a time for now
                long newlyCommittedCount = RaftActorManager.INSTANCE.getActors().stream()
                        .filter(actor -> leaderData.getMatchIndex(actor) > commitIndex)
                        .count();

                if (newlyCommittedCount > RaftActorManager.INSTANCE.getActors().size()/2.0) {
                    commitIndex++;
                    // TODO execute command
                    logger.info(getSelf().hashCode() + " leader commit has increased to " + commitIndex);
                }
            } else {
                leaderData.setNextIndex(getSender(), leaderData.getNextIndex(getSender())-1);
            }
        }

        return nextState;
    }

    private State<RaftStateMachine.State, RaftStateMachine.PersistentData> syncTerm(RaftMessage message) {
        // check if we are out of sync
        final long sendersTerm = message.getTerm();
        if (sendersTerm > stateData().getTerm()) {
            stateData().newTerm(sendersTerm);

            // fail to follower if were not one already
            if (stateName() != RaftStateMachine.State.FOLLOWER) return goTo(RaftStateMachine.State.FOLLOWER);
        }
        return stay();
    }

    private long getTermFromLog(int index) {
        final Log log = stateData().getLog();
        if (index < 0 || index >= log.size()) {
            return 0;
        }
        return log.getEntry(index).getTerm();
    }

    private int getId() {
        return hashCode(); // FIXME guaranteed unique for now... Should be port number over network
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
