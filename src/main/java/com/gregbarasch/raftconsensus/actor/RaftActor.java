package com.gregbarasch.raftconsensus.actor;

import akka.actor.AbstractFSM;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.pf.FSMStateFunctionBuilder;
import akka.japi.pf.FSMTransitionHandlerBuilder;
import com.gregbarasch.raftconsensus.messaging.AppendEntriesRequestMessage;
import com.gregbarasch.raftconsensus.messaging.VoteRequestDto;
import com.gregbarasch.raftconsensus.messaging.VoteResponseDto;
import org.apache.log4j.Logger;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static com.gregbarasch.raftconsensus.actor.RaftStateMachine.State.*;

// TODO persist stuff to the disk

/**
After the entry is committed, the leader executes the entry and responds back with the result to the client.
        It should be noted that these entries are executed in the order they are received.
*/

// TODO followers only apply current term entries. only if suffix is compatible
// TODO followers only refuse an update if therers an earlierr conflict : leader will send longer suffix next time
// TODO followers overwrites tail of matching log with new suffix
// TODO leader sends its last entrry, followers might reject if the suffix is bad and follower will respond with their good 1

class RaftActor extends AbstractFSM<RaftStateMachine.State, RaftStateMachine.Data> {
    private static final Logger logger = Logger.getLogger(RaftActor.class);

    // Used for elections
    private Set<ActorRef> actorsDidntVoteYesSet;

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
                        .event(VoteRequestDto.class, (request, data) -> onRequestVoteDto(request))
                        .event(VoteResponseDto.class, (vote, data) -> onVoteDto(vote)) // TODO maybe dont handle
                        .event(AppendEntriesRequestMessage.class, (message, data) -> stay()) // FIXME
                        .build()
        );

        when(CANDIDATE,
                new FSMStateFunctionBuilder<RaftStateMachine.State, RaftStateMachine.Data>()
                        .event(Timeout.class, (timeoutMessage, data) -> {
                            resetTimeout();
                            return goTo(CANDIDATE);
                        })
                        .event(VoteRequestDto.class, (request, data) -> onRequestVoteDto(request))
                        .event(VoteResponseDto.class, (vote, data) -> onVoteDto(vote))
                        .build()
        );

        when(LEADER,
                new FSMStateFunctionBuilder<RaftStateMachine.State, RaftStateMachine.Data>()
                        .event(Timeout.class, (timeoutMessage, data) -> {
                            resetTimeout();
                            return goTo(FOLLOWER); // FIXME do leadersr actually timeout?
                        })
                        .event(VoteRequestDto.class, (request, data) -> onRequestVoteDto(request)) // TODO maybe unhandle?
                        .event(VoteResponseDto.class, (vote, data) -> onVoteDto(vote)) // TODO maybe unhandle?
                        .build()
        );

        onTransition(
                new FSMTransitionHandlerBuilder<RaftStateMachine.State>()
                        .state(FOLLOWER, CANDIDATE, this::election)
                        .state(CANDIDATE, CANDIDATE, this::election)
                        .state(CANDIDATE, FOLLOWER, () -> {})
                        .state(CANDIDATE, LEADER, () -> {
                            // FIXME? make sure timers work properly
                            logger.info(getSelf().hashCode() + " has become the leader");
                            resetTimeout();
                        })
                        .state(LEADER, FOLLOWER, () -> {
                            logger.info(getSelf().hashCode() + " is no longer the leader");
                        })
                        // TODO we can go to a follower if we discover our term is fxxd
                        .build()
        );

        startWith(FOLLOWER, new RaftStateMachine.Data());
        startTimeout();
    }

    private void election() {
        stateData().nextTerm();
        logger.info(getSelf().hashCode() + " has started an election in term " + stateData().getTerm());

        // vote for self
        actorsDidntVoteYesSet = new HashSet<>(RaftActorManager.INSTANCE.getActors());
        actorsDidntVoteYesSet.remove(getSelf());
        stateData().votedFor(getSelf());

        // Attempt to receive votes from majority of actors
        final VoteRequestDto voteRequestDto = new VoteRequestDto(stateData().getTerm());
        for (final ActorRef actor : new ArrayList<>(actorsDidntVoteYesSet)) {
            actor.tell(voteRequestDto, getSelf()); // TODO request again
        }
    }

    private State<RaftStateMachine.State, RaftStateMachine.Data> onRequestVoteDto(VoteRequestDto voteRequestDto) {
        // FIXME leader will vote no??

        // sync up with senders term // FIXME fail to follower? but not if were already follower?
        final long sendersTerm = voteRequestDto.getTerm();
        if (sendersTerm > stateData().getTerm()) {
            stateData().newTerm(sendersTerm);
        }

        // Only vote yes once per term. // FIXME vote only granted to nodes with more up to date logs...
        boolean vote = false;
        if (stateData().votedFor() == null) {
            stateData().votedFor(getSender());
            vote = true;
        }

        VoteResponseDto voteResponseDto = new VoteResponseDto(stateData().getTerm(), vote);
        getSender().tell(voteResponseDto, getSelf());

        return stay();
    }

    private State<RaftStateMachine.State, RaftStateMachine.Data> onVoteDto(VoteResponseDto voteResponseDto) {

        // Fail to follower if we are out of sync
        final long sendersTerm = voteResponseDto.getTerm();
        if (sendersTerm > stateData().getTerm()) {
            stateData().newTerm(sendersTerm);
            return goTo(FOLLOWER);
        }

        // Remove
        if (voteResponseDto.isYes()) {
            actorsDidntVoteYesSet.remove(getSender());
        }

        // If we have enough votes, become the leader
        if (actorsDidntVoteYesSet.size() <= RaftActorManager.INSTANCE.getActors().size()/2) {
            return goTo(LEADER);
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

    private enum Timeout {
        INSTANCE
    }
}
