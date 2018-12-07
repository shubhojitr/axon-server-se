package io.axoniq.axonserver.cluster;

import io.axoniq.axonserver.grpc.cluster.AppendEntriesRequest;
import io.axoniq.axonserver.grpc.cluster.AppendEntriesResponse;
import io.axoniq.axonserver.grpc.cluster.InstallSnapshotRequest;
import io.axoniq.axonserver.grpc.cluster.InstallSnapshotResponse;
import io.axoniq.axonserver.grpc.cluster.RequestVoteRequest;
import io.axoniq.axonserver.grpc.cluster.RequestVoteResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * @author Sara Pellegrini
 * @since 4.0
 */
public class CandidateState extends AbstractMembershipState {

    private static final Logger logger = LoggerFactory.getLogger(CandidateState.class);
    private final AtomicReference<Registration> nextElection = new AtomicReference<>();
    private final AtomicReference<Election> currentElection = new AtomicReference<>();

    private CandidateState(Builder builder) {
        super(builder);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void start() {
        startElection();
    }

    @Override
    public void stop() {
        Optional.ofNullable(nextElection.get()).ifPresent(Registration::cancel);
    }

    @Override
    public synchronized AppendEntriesResponse appendEntries(AppendEntriesRequest request) {
        if (request.getTerm() >= currentTerm()) {
            return handleAsFollower(follower -> follower.appendEntries(request));
        }
        return appendEntriesFailure();
    }

    @Override
    public synchronized RequestVoteResponse requestVote(RequestVoteRequest request) {
        if (request.getTerm() > currentTerm()) {
            RequestVoteResponse vote = handleAsFollower(follower -> follower.requestVote(request));
            logger.debug("Request for vote received from {} in term {}. {} voted {}", request.getCandidateId(), request.getTerm(), me(), vote != null && vote.getVoteGranted());
            return vote;
        }
        logger.debug("Request for vote received from {} in term {}. {} voted rejected", request.getCandidateId(), request.getTerm(), me());
        return requestVoteResponse(false);
    }

    @Override
    public synchronized InstallSnapshotResponse installSnapshot(InstallSnapshotRequest request) {
        if (request.getTerm() > currentTerm()) {
            return handleAsFollower(follower -> follower.installSnapshot(request));
        }
        return installSnapshotFailure();
    }

    private void resetElectionTimeout() {
        int timeout = random(minElectionTimeout(), maxElectionTimeout() + 1);
        Registration newTask = scheduler().schedule(this::startElection, timeout, MILLISECONDS);
        nextElection.set(newTask);
    }

    private void startElection() {
        try {
            synchronized (this) {
                updateCurrentTerm(currentTerm() + 1);
                markVotedFor(me());
            }
            logger.info("{}: Starting election from {} in term {}", groupId(), me(), currentTerm());
            resetElectionTimeout();
            currentElection.set(new CandidateElection(this::clusterSize));
            currentElection.get().registerVoteReceived(me(), true);
            RequestVoteRequest request = requestVote();
            Collection<RaftPeer> raftPeers = otherNodes();
            if (raftPeers.isEmpty() && !raftGroup().raftConfiguration().groupMembers().isEmpty()) {
                currentElection.set(null);
                changeStateTo(stateFactory().leaderState());
            } else {
                raftPeers.forEach(node -> requestVote(request, node));
            }
        } catch (Exception ex) {
            logger.warn("Failed to start election", ex);
        }
    }

    private RequestVoteRequest requestVote() {
        TermIndex lastLog = lastLog();
        return RequestVoteRequest.newBuilder()
                                 .setGroupId(groupId())
                                 .setCandidateId(me())
                                 .setTerm(currentTerm())
                                 .setLastLogIndex(lastLog.getIndex())
                                 .setLastLogTerm(lastLog.getTerm())
                                 .build();
    }

    private void requestVote(RequestVoteRequest request, RaftPeer node) {
        node.requestVote(request).thenAccept(response -> onVoteResponse(node.nodeId(), response));
    }

    private synchronized void onVoteResponse(String voter, RequestVoteResponse response) {
        if (response.getTerm() > currentTerm()) {
            changeStateTo(stateFactory().followerState());
            return;
        }
        //The candidate can receive a response with lower term if the voter is receiving regular heartbeat from a leader.
        //In this case, the voter recognizes any request of vote as disruptive, refuses the vote and does't update its term.
        if (response.getTerm() < currentTerm()) {
            return;
        }
        Election election = this.currentElection.get();
        if (election != null) {
            election.registerVoteReceived(voter, response.getVoteGranted());
            if (election.isWon()) {
                this.currentElection.set(null);
                changeStateTo(stateFactory().leaderState());
            }
        }
    }

    public static class Builder extends AbstractMembershipState.Builder<Builder> {
        public CandidateState build() {
            return new CandidateState(this);
        }
    }
}
