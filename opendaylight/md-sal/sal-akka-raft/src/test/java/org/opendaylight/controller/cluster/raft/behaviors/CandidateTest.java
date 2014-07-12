package org.opendaylight.controller.cluster.raft.behaviors;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.testkit.JavaTestKit;
import junit.framework.Assert;
import org.junit.Test;
import org.opendaylight.controller.cluster.raft.MockRaftActorContext;
import org.opendaylight.controller.cluster.raft.RaftActorContext;
import org.opendaylight.controller.cluster.raft.RaftState;
import org.opendaylight.controller.cluster.raft.internal.messages.ElectionTimeout;
import org.opendaylight.controller.cluster.raft.messages.AppendEntries;
import org.opendaylight.controller.cluster.raft.messages.AppendEntriesReply;
import org.opendaylight.controller.cluster.raft.messages.RequestVote;
import org.opendaylight.controller.cluster.raft.messages.RequestVoteReply;
import org.opendaylight.controller.cluster.raft.utils.DoNothingActor;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class CandidateTest extends AbstractRaftActorBehaviorTest {

    private final ActorRef candidateActor = getSystem().actorOf(Props.create(
        DoNothingActor.class));

    private final ActorRef peerActor1 = getSystem().actorOf(Props.create(
        DoNothingActor.class));

    private final ActorRef peerActor2 = getSystem().actorOf(Props.create(
        DoNothingActor.class));

    private final ActorRef peerActor3 = getSystem().actorOf(Props.create(
        DoNothingActor.class));

    private final ActorRef peerActor4 = getSystem().actorOf(Props.create(
        DoNothingActor.class));

    @Test
    public void testWhenACandidateIsCreatedItIncrementsTheCurrentTermAndVotesForItself(){
        RaftActorContext raftActorContext = createActorContext();
        long expectedTerm = raftActorContext.getTermInformation().getCurrentTerm().get();

        new Candidate(raftActorContext, Collections.EMPTY_LIST);

        assertEquals(expectedTerm+1, raftActorContext.getTermInformation().getCurrentTerm().get());
        assertEquals(raftActorContext.getId(), raftActorContext.getTermInformation().getVotedFor());
    }

    @Test
    public void testThatAnElectionTimeoutIsTriggered(){
        new JavaTestKit(getSystem()) {{

            new Within(duration("1 seconds")) {
                protected void run() {

                    Candidate candidate = new Candidate(createActorContext(getTestActor()), Collections.EMPTY_LIST);

                    final Boolean out = new ExpectMsg<Boolean>(duration("1 seconds"), "ElectionTimeout") {
                        // do not put code outside this method, will run afterwards
                        protected Boolean match(Object in) {
                            if (in instanceof ElectionTimeout) {
                                 return true;
                            } else {
                                throw noMatch();
                            }
                        }
                    }.get();

                    assertEquals(true, out);
                }
            };
        }};
    }

    @Test
    public void testHandleElectionTimeoutWhenThereAreZeroPeers(){
        RaftActorContext raftActorContext = createActorContext();
        Candidate candidate =
            new Candidate(raftActorContext, Collections.EMPTY_LIST);

        RaftState raftState =
            candidate.handleMessage(candidateActor, new ElectionTimeout());

        Assert.assertEquals(RaftState.Leader, raftState);
    }

    @Test
    public void testHandleElectionTimeoutWhenThereAreTwoPeers(){
        RaftActorContext raftActorContext = createActorContext();
        Candidate candidate =
            new Candidate(raftActorContext, Arrays
                .asList(peerActor1.path().toString(),
                    peerActor2.path().toString()));

        RaftState raftState =
            candidate.handleMessage(candidateActor, new ElectionTimeout());

        Assert.assertEquals(RaftState.Candidate, raftState);
    }

    @Test
    public void testBecomeLeaderOnReceivingMajorityVotesInThreePeerCluster(){
        RaftActorContext raftActorContext = createActorContext();
        Candidate candidate =
            new Candidate(raftActorContext, Arrays
                .asList(peerActor1.path().toString(),
                    peerActor2.path().toString()));

        RaftState stateOnFirstVote = candidate.handleMessage(peerActor1, new RequestVoteReply(0, true));

        Assert.assertEquals(RaftState.Leader, stateOnFirstVote);

    }

    @Test
    public void testBecomeLeaderOnReceivingMajorityVotesInFivePeerCluster(){
        RaftActorContext raftActorContext = createActorContext();
        Candidate candidate =
            new Candidate(raftActorContext, Arrays
                .asList(peerActor1.path().toString(),
                    peerActor2.path().toString(),
                    peerActor3.path().toString()));

        RaftState stateOnFirstVote = candidate.handleMessage(peerActor1, new RequestVoteReply(0, true));

        RaftState stateOnSecondVote = candidate.handleMessage(peerActor1, new RequestVoteReply(0, true));

        Assert.assertEquals(RaftState.Candidate, stateOnFirstVote);
        Assert.assertEquals(RaftState.Leader, stateOnSecondVote);

    }

    @Test
    public void testResponseToAppendEntriesWithLowerTerm(){
        new JavaTestKit(getSystem()) {{

            new Within(duration("1 seconds")) {
                protected void run() {

                    Candidate candidate = new Candidate(createActorContext(getTestActor()), Collections.EMPTY_LIST);

                    candidate.handleMessage(getTestActor(), new AppendEntries(0, "test", 0,0,Collections.EMPTY_LIST, 0));

                    final Boolean out = new ExpectMsg<Boolean>(duration("1 seconds"), "AppendEntriesResponse") {
                        // do not put code outside this method, will run afterwards
                        protected Boolean match(Object in) {
                            if (in instanceof AppendEntriesReply) {
                                AppendEntriesReply reply = (AppendEntriesReply) in;
                                return reply.isSuccess();
                            } else {
                                throw noMatch();
                            }
                        }
                    }.get();

                    assertEquals(false, out);
                }
            };
        }};
    }

    @Test
    public void testResponseToRequestVoteWithLowerTerm(){
        new JavaTestKit(getSystem()) {{

            new Within(duration("1 seconds")) {
                protected void run() {

                    Candidate candidate = new Candidate(createActorContext(getTestActor()), Collections.EMPTY_LIST);

                    candidate.handleMessage(getTestActor(), new RequestVote(0, "test", 0, 0));

                    final Boolean out = new ExpectMsg<Boolean>(duration("1 seconds"), "AppendEntriesResponse") {
                        // do not put code outside this method, will run afterwards
                        protected Boolean match(Object in) {
                            if (in instanceof RequestVoteReply) {
                                RequestVoteReply reply = (RequestVoteReply) in;
                                return reply.isVoteGranted();
                            } else {
                                throw noMatch();
                            }
                        }
                    }.get();

                    assertEquals(false, out);
                }
            };
        }};
    }



    @Override protected RaftActorBehavior createBehavior(RaftActorContext actorContext) {
        return new Candidate(actorContext, Collections.EMPTY_LIST);
    }

    @Override protected RaftActorContext createActorContext() {
        return new MockRaftActorContext("test", getSystem(), candidateActor);
    }

    protected RaftActorContext createActorContext(ActorRef candidateActor) {
        return new MockRaftActorContext("test", getSystem(), candidateActor);
    }

}
