package com.gregbarasch.raftconsensus.actor;

import akka.actor.AbstractFSM;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.pf.FSMStateFunctionBuilder;
import akka.japi.pf.FSMTransitionHandlerBuilder;
import com.gregbarasch.raftconsensus.messaging.RequestVoteDto;
import com.gregbarasch.raftconsensus.messaging.TimeoutMessage;
import com.gregbarasch.raftconsensus.messaging.VoteDto;
import org.apache.log4j.Logger;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static com.gregbarasch.raftconsensus.actor.RaftStateMachine.State.*;

// FIXME
// at what point do we increment term?
// at what point do we set requestTimeout

class RaftActor extends AbstractFSM<RaftStateMachine.State, RaftStateMachine.Data> {
    private static final Logger logger = Logger.getLogger(RaftActor.class);

    // Used for elections
    private Set<ActorRef> actorsDidntVoteYesSet;

    static Props props() {
        return Props.create(RaftActor.class);
    }

    private RaftActor() {
        onTransition(
                new FSMTransitionHandlerBuilder<RaftStateMachine.State>()
                        .state(FOLLOWER, CANDIDATE, this::election)
                        .state(CANDIDATE, CANDIDATE, this::election)
                        .state(CANDIDATE, FOLLOWER, () -> {})
                        .state(CANDIDATE, LEADER, () -> {
                            // FIXME? make sure timers work properly
                            logger.info(getSelf().hashCode() + " has become the leader");
                            cancelTimeout();
                        })
                        .state(LEADER, FOLLOWER, () -> {
                            logger.info(getSelf().hashCode() + " is no longer the leader");
                        })
                        // TODO we can go to a follower if we discover our term is fxxd
                        .build()
        );

        when(FOLLOWER,
                new FSMStateFunctionBuilder<RaftStateMachine.State, RaftStateMachine.Data>()
                        .event(TimeoutMessage.class, (timeout, data) -> {
                            resetTimeout();
                            return goTo(CANDIDATE);
                        })
                        .event(RequestVoteDto.class, (request, data) -> onRequestVoteDto(request))
                        .event(VoteDto.class, (vote, data) -> onVoteDto(vote)) // TODO maybe dont handle
                        .build()
        );

        when(CANDIDATE,
                new FSMStateFunctionBuilder<RaftStateMachine.State, RaftStateMachine.Data>()
                        .event(TimeoutMessage.class, (timeoutMessage, data) -> {
                            resetTimeout();
                            return goTo(CANDIDATE);
                        })
                        .event(RequestVoteDto.class, (request, data) -> onRequestVoteDto(request))
                        .event(VoteDto.class, (vote, data) -> onVoteDto(vote))
                        .build()
        );

        when(LEADER,
                new FSMStateFunctionBuilder<RaftStateMachine.State, RaftStateMachine.Data>()
                        .event(TimeoutMessage.class, (timeoutMessage, data) -> {
                            resetTimeout();
                            return goTo(CANDIDATE); // FIXME goto follower?
                        })
                        .event(RequestVoteDto.class, (request, data) -> onRequestVoteDto(request)) // TODO maybe unhandle?
                        .event(VoteDto.class, (vote, data) -> onVoteDto(vote)) // TODO maybe unhandle?
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
        final RequestVoteDto requestVoteDto = new RequestVoteDto(stateData().getTerm());
        for (final ActorRef actor : new ArrayList<>(actorsDidntVoteYesSet)) {
            actor.tell(requestVoteDto, getSelf()); // TODO request again
        }
    }

    private State<RaftStateMachine.State, RaftStateMachine.Data> onRequestVoteDto(RequestVoteDto requestVoteDto) {
        // FIXME leader will vote no??

        // sync up with senders term // FIXME fail to follower? but not if were already follower?
        final long sendersTerm = requestVoteDto.getTerm();
        if (sendersTerm > stateData().getTerm()) {
            stateData().newTerm(sendersTerm);
        }

        // Only vote yes once per term.
        boolean vote = false;
        if (stateData().votedFor() == null) {
            stateData().votedFor(getSender());
            vote = true;
        }

        VoteDto voteDto = new VoteDto(stateData().getTerm(), vote);
        getSender().tell(voteDto, getSelf());

        return stay();
    }

    private State<RaftStateMachine.State, RaftStateMachine.Data> onVoteDto(VoteDto voteDto) {

        // Fail to follower if we are out of sync
        final long sendersTerm = voteDto.getTerm();
        if (sendersTerm > stateData().getTerm()) {
            stateData().newTerm(sendersTerm);
            return goTo(FOLLOWER);
        }

        // Remove
        if (voteDto.isYes()) {
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
        setTimer(TimeoutMessage.TIMEOUT.name(), TimeoutMessage.TIMEOUT, Duration.ofMillis(timeoutMillis));
    }

    private void cancelTimeout() {
        cancelTimer(TimeoutMessage.TIMEOUT.name());
    }

    private void resetTimeout() {
        cancelTimeout();
        startTimeout();
    }
}
