package com.gregbarasch.raftconsensus.actor;

import akka.actor.ActorSystem;
import akka.testkit.TestActorRef;
import com.gregbarasch.raftconsensus.model.RaftStateMachine;
import org.junit.Test;

public class RaftActorTest {

    @Test
    public void testIt() {
        ActorSystem system = ActorSystem.create();
        final TestActorRef<RaftActor> ref = TestActorRef.create(system, RaftActor.props());
        final RaftActor actor = ref.underlyingActor();

        // Change to leader
        actor.applyState(actor.goTo(RaftStateMachine.State.LEADER));

        // FIXME to be continued...
    }
}
