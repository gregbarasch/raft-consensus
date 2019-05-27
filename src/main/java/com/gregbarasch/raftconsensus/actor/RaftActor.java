package com.gregbarasch.raftconsensus.actor;

import akka.actor.AbstractFSM;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.pf.FSMStateFunctionBuilder;
import akka.japi.pf.FSMTransitionHandlerBuilder;
import com.gregbarasch.raftconsensus.messaging.AppendEntriesRequestDto;
import com.gregbarasch.raftconsensus.messaging.AppendEntriesResponseDto;
import com.gregbarasch.raftconsensus.messaging.CommandRequestDto;
import com.gregbarasch.raftconsensus.messaging.CommandResponseDto;
import com.gregbarasch.raftconsensus.messaging.RaftMessage;
import com.gregbarasch.raftconsensus.messaging.RaftResponseMessage;
import com.gregbarasch.raftconsensus.messaging.VoteRequestDto;
import com.gregbarasch.raftconsensus.messaging.VoteResponseDto;
import com.gregbarasch.raftconsensus.model.Command;
import com.gregbarasch.raftconsensus.model.Log;
import com.gregbarasch.raftconsensus.model.PersistentActorData;
import com.gregbarasch.raftconsensus.model.RaftStateMachine;
import com.gregbarasch.raftconsensus.model.VolatileLeaderData;
import org.apache.log4j.Logger;

import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static com.gregbarasch.raftconsensus.model.RaftStateMachine.State.*;

// TODO commandresponsedto... On failed response retry with leader
// TODO state machine
// TODO use commandID in commandresponse dto so client knows if its a repeat.. persist commandID in log

class RaftActor extends AbstractFSM<RaftStateMachine.State, RaftStateMachine.StateData> {

    private static final Logger logger = Logger.getLogger(RaftActor.class);

    // Used for elections
    private Set<ActorRef> actorsDidntVoteYesSet;

    private ActorRef leader = null;

    private PersistentActorData persistentActorData = null;
    private VolatileLeaderData volatileLeaderData = null;

    private int commitIndex = -1;
    private int lastApplied = -1; // TODO this is used to say which entry was last executed

    static Props props() {
        return Props.create(RaftActor.class);
    }

    private RaftActor() {
        when(FOLLOWER,
                new FSMStateFunctionBuilder<RaftStateMachine.State, RaftStateMachine.StateData>()
                        .event(Timeout.class, (timeout, data) -> {
                            resetTimeout(Timeout.ELECTION);
                            return goTo(CANDIDATE);
                        })
                        .event(AppendEntriesRequestDto.class, (message, data) -> onAppendEntriesRequestDto(message))
                        .event(VoteRequestDto.class, (request, data) -> onVoteRequestDto(request))
                        .build()
        );

        when(CANDIDATE,
                new FSMStateFunctionBuilder<RaftStateMachine.State, RaftStateMachine.StateData>()
                        .event(Timeout.class, (timeout, data) -> {
                            resetTimeout(Timeout.ELECTION);
                            return goTo(CANDIDATE);
                        })
                        .event(AppendEntriesRequestDto.class, (message, data) -> onAppendEntriesRequestDto(message))
                        .event(VoteRequestDto.class, (request, data) -> onVoteRequestDto(request))
                        .event(VoteResponseDto.class, (vote, data) -> termConfusion(vote) ? stay() : onVoteResponseDto(vote))
                        .build()
        );

        when(LEADER,
                new FSMStateFunctionBuilder<RaftStateMachine.State, RaftStateMachine.StateData>()
                        .event(Timeout.class, (timeout, data) -> {
                            resetTimeout(Timeout.HEARTBEAT);
                            appendEntries();
                            return stay();
                        })
                        .event(AppendEntriesRequestDto.class, (message, data) -> onAppendEntriesRequestDto(message))
                        .event(AppendEntriesResponseDto.class, (response, data) -> termConfusion(response) ? stay() : onAppendEntriesResponseDto(response))
                        .event(VoteRequestDto.class, (request, data) -> onVoteRequestDto(request))
                        .event(CommandRequestDto.class, (commandRequest, data) -> {
                            logger.info(getSelf().hashCode() + " received command: " + commandRequest);
                            final com.gregbarasch.raftconsensus.model.LogEntry logEntry = new com.gregbarasch.raftconsensus.model.LogEntry(
                                    new Command(commandRequest.getAmount(), commandRequest.getRequestId()),
                                    persistentActorData.getLog().size(),
                                    persistentActorData.getTerm());
                            persistentActorData.getLog().putEntries(Collections.singletonList(logEntry));
                            return stay();
                        })
                        .build()
        );

        whenUnhandled(new FSMStateFunctionBuilder<RaftStateMachine.State, RaftStateMachine.StateData>()
                .event(CommandRequestDto.class, (event, data) -> {
                    // Reroute
                    getSender().tell(new CommandResponseDto(leader), getSelf()); // FIXME
                    return stay();
                })
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

        persistentActorData = new PersistentActorData(getId());
        startWith(FOLLOWER, new RaftStateMachine.StateData(getId()));
        startTimeout(Timeout.ELECTION);
    }

    private void handleLeaderTransition() {
        // reset volatileLeaderData
        final com.gregbarasch.raftconsensus.model.LogEntry lastLogEntry = persistentActorData.getLog().getLastEntry();
        if (lastLogEntry == null) {
            volatileLeaderData = new VolatileLeaderData(-1);
        } else {
            volatileLeaderData = new VolatileLeaderData(lastLogEntry.getIndex());
        }

        // Register self with system as leader
        leader = getSelf();
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
            final int actorNextIndex = volatileLeaderData.getNextIndex(actor);
            if (actorNextIndex <= persistentActorData.getLog().size()) {

                // set some values
                final int toIndex = Math.min(persistentActorData.getLog().size(), actorNextIndex+1); // For now we should only be sending 1 entry
                final int prevIndex = actorNextIndex-1;
                long prevTerm = getTermFromLog(prevIndex);

                // construct request
                final List<com.gregbarasch.raftconsensus.model.LogEntry> logEntries = persistentActorData.getLog().subLog(actorNextIndex, toIndex);
                final AppendEntriesRequestDto request = new AppendEntriesRequestDto(
                        persistentActorData.getTerm(),
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
        persistentActorData.nextTerm();
        logger.info(getSelf().hashCode() + " has started an election in term " + persistentActorData.getTerm());

        // vote for self
        actorsDidntVoteYesSet = new HashSet<>(RaftActorManager.INSTANCE.getActors());
        actorsDidntVoteYesSet.remove(getSelf());
        persistentActorData.votedFor(getSelf());

        // get lastLogEntry related info
        final com.gregbarasch.raftconsensus.model.LogEntry lastLogEntry = persistentActorData.getLog().getLastEntry();

        Integer lastLogIndex = null;
        Long lastLogTerm = null;
        if (lastLogEntry != null) {
            lastLogIndex = lastLogEntry.getIndex();
            lastLogTerm = lastLogEntry.getTerm();
        }

        // Send vote request to all servers
        final VoteRequestDto voteRequestDto = new VoteRequestDto(persistentActorData.getTerm(), lastLogIndex, lastLogTerm);
        for (final ActorRef actor : actorsDidntVoteYesSet) {
            actor.tell(voteRequestDto, getSelf()); // TODO request vote again with a timer for actors not received?
        }
    }

    private State<RaftStateMachine.State, RaftStateMachine.StateData> onVoteRequestDto(VoteRequestDto voteRequestDto) {

        final State<RaftStateMachine.State, RaftStateMachine.StateData> nextState = syncTerm(voteRequestDto);

        // Only vote yes once per term
        boolean vote = false;
        if (persistentActorData.votedFor() == null) {

            final com.gregbarasch.raftconsensus.model.LogEntry lastLogEntry = persistentActorData.getLog().getLastEntry();
            final Integer requestLogIndex = voteRequestDto.getLogIndex();
            final Long requestLogTerm = voteRequestDto.getLogTerm();

            // If everything is unset, we can grant a vote
            if ((lastLogEntry == null && requestLogIndex == null && requestLogTerm == null)) {
                resetTimeout(Timeout.ELECTION);
                persistentActorData.votedFor(getSender());
                vote = true;
            } else {
                // Otherwise, only vote for nodes whose logs are up to date
                if (lastLogEntry != null
                        && requestLogIndex.compareTo(lastLogEntry.getIndex()) >= 0
                        && requestLogTerm.compareTo(lastLogEntry.getTerm()) >= 0) {
                    persistentActorData.votedFor(getSender());
                    vote = true;
                }
            }
        }

        // persist and send response
        persistentActorData.persistToDisk();
        VoteResponseDto voteResponseDto = new VoteResponseDto(persistentActorData.getTerm(), voteRequestDto.getTerm(), vote);
        getSender().tell(voteResponseDto, getSelf());

        return nextState;
    }

    private State<RaftStateMachine.State, RaftStateMachine.StateData> onVoteResponseDto(VoteResponseDto voteResponseDto) {

        State<RaftStateMachine.State, RaftStateMachine.StateData> nextState = syncTerm(voteResponseDto);

        // Remove
        if (voteResponseDto.isYes()) {
            actorsDidntVoteYesSet.remove(getSender());
        }

        // If we have enough votes, become the leader
        if (actorsDidntVoteYesSet.size() < (RaftActorManager.INSTANCE.getActors().size() / 2.0)) {
            nextState = goTo(LEADER); // Should only receive vote when our term is newest
        }

        return nextState;
    }

    private State<RaftStateMachine.State, RaftStateMachine.StateData> onAppendEntriesRequestDto(AppendEntriesRequestDto request) {

        leader = getSender();
        final long termPreUpdate = persistentActorData.getTerm();
        final State<RaftStateMachine.State, RaftStateMachine.StateData> nextState = syncTerm(request);

        boolean success = false;
        int matchIndex = -1;

        // Fail fast if our terms are out of sync
        if (termPreUpdate == persistentActorData.getTerm()) {

            if (request.getTerm() >= persistentActorData.getTerm()) {
                resetTimeout(Timeout.ELECTION);

                // Reply false if log doesn’t contain an entry at prevLogIndex whose term matches prevLogTerm
                final long prevTerm = getTermFromLog(request.getPrevLogIndex());
                if (request.getPrevLogIndex() == -1
                        || (request.getPrevLogIndex() < persistentActorData.getLog().size()
                        && prevTerm == request.getPrevLogTerm())) {

                    // append entries on success
                    if (request.getEntries().size() > 0) {
                        persistentActorData.getLog().putEntries(request.getEntries());
                    }

                    success = true;
                    matchIndex = persistentActorData.getLog().size() - 1;

                    // update commit
                    final int prevCommitIndex = commitIndex;
                    commitIndex = Math.min(matchIndex, request.getCommitIndex()); // sender should have enough info to ensure that commitIndex sent is <= receivers log.size()-1
                    if (prevCommitIndex != commitIndex) {
                        logger.debug(getSelf().hashCode() + " commitIndex set to: " + commitIndex);
                    }
                }
            }
        }

        // persist to disk and send response
        persistentActorData.persistToDisk();
        final AppendEntriesResponseDto response = new AppendEntriesResponseDto(persistentActorData.getTerm(), request.getTerm(), success, matchIndex);
        getSender().tell(response, getSelf());
        return nextState;
    }

    private State<RaftStateMachine.State, RaftStateMachine.StateData> onAppendEntriesResponseDto(AppendEntriesResponseDto response) {

        final long termPreUpdate = persistentActorData.getTerm();
        final State<RaftStateMachine.State, RaftStateMachine.StateData> nextState = syncTerm(response);

        if (persistentActorData.getTerm() == response.getTerm()) {
            if (response.isSuccess()) {
                volatileLeaderData.setMatchIndex(getSender(), response.getMatchIndex());
                volatileLeaderData.setNextIndex(getSender(), response.getMatchIndex()+1);

                long newlyCommittedCount = RaftActorManager.INSTANCE.getActors().stream()
                        .filter(actor -> volatileLeaderData.getMatchIndex(actor) > commitIndex)
                        .count();

                // Commit everything new...
                while (newlyCommittedCount > RaftActorManager.INSTANCE.getActors().size()/2.0) {
                    // TODO execute command and respond to client
                    commitIndex++;
                    logger.info(getSelf().hashCode() + " leader commit has increased to " + commitIndex);

                    newlyCommittedCount = RaftActorManager.INSTANCE.getActors().stream()
                            .filter(actor -> volatileLeaderData.getMatchIndex(actor) > commitIndex)
                            .count();
                }


            } else if (termPreUpdate == persistentActorData.getTerm()) {
                // term check avoids race condition for setting nextIndex on immediate re-election
                volatileLeaderData.setNextIndex(getSender(), volatileLeaderData.getNextIndex(getSender())-1);
            }
        }

        return nextState;
    }

    private State<RaftStateMachine.State, RaftStateMachine.StateData> syncTerm(RaftMessage message) {
        // check if we are out of sync
        final long sendersTerm = message.getTerm();
        if (sendersTerm > persistentActorData.getTerm()) {
            persistentActorData.newTerm(sendersTerm);

            // fail to follower if were not one already
            if (stateName() != FOLLOWER) return goTo(FOLLOWER);
        }
        return stay();
    }

    private long getTermFromLog(int index) {
        final Log log = persistentActorData.getLog();
        if (index < 0 || index >= log.size()) {
            return 0;
        }
        return log.getEntry(index).getTerm();
    }

    private boolean termConfusion(RaftResponseMessage response) {
        return response.getOriginalRequestTerm() != persistentActorData.getTerm();
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
