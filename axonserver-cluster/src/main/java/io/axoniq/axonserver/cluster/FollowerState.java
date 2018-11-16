package io.axoniq.axonserver.cluster;

import io.axoniq.axonserver.cluster.replication.LogEntryStore;
import io.axoniq.axonserver.grpc.cluster.AppendEntriesRequest;
import io.axoniq.axonserver.grpc.cluster.AppendEntriesResponse;
import io.axoniq.axonserver.grpc.cluster.AppendEntrySuccess;
import io.axoniq.axonserver.grpc.cluster.InstallSnapshotRequest;
import io.axoniq.axonserver.grpc.cluster.InstallSnapshotResponse;
import io.axoniq.axonserver.grpc.cluster.RequestVoteRequest;
import io.axoniq.axonserver.grpc.cluster.RequestVoteResponse;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static java.lang.Math.min;

public class FollowerState extends AbstractMembershipState {

    private final ScheduledExecutorService scheduledExecutorService;

    private ScheduledFuture<?> scheduledElection;
    private long lastMessageReceivedAt;

    protected FollowerState(Builder builder) {
        super(builder);
        this.scheduledExecutorService = builder.scheduledExecutorService;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void start() {
        lastMessageReceivedAt = 0;
        scheduleNewElection();
    }

    @Override
    public void stop() {
        cancelCurrentElectionTimeout();
        scheduledExecutorService.shutdown();
    }

    @Override
    public AppendEntriesResponse appendEntries(AppendEntriesRequest request) {
        newMessageReceived(request.getTerm());
        LogEntryStore logEntryStore = raftGroup().localLogEntryStore();

        //1. Reply false if term < currentTerm
        if (request.getTerm() < currentTerm()) {
            return appendEntriesFailure();
        }

        //2. Reply false if log doesn't contain an entry at prevLogIndex whose term matches prevLogTerm
        if (!logEntryStore.contains(request.getPrevLogIndex(), request.getPrevLogTerm())) {
            return appendEntriesFailure();
        }

        //3. If an existing entry conflicts with a new one (same index but different terms), delete the existing entry
        // and all that follow it
        //4. Append any new entries not already in the log
        try {
            raftGroup().localLogEntryStore().appendEntry(request.getEntriesList());
        } catch (IOException e) {
            stop();
            return appendEntriesFailure();
        }

        //5. If leaderCommit > commitIndex, set commitIndex = min(leaderCommit, index of last new entry)
        if (request.getCommitIndex() > logEntryStore.commitIndex()) {
            logEntryStore.markCommitted(min(request.getCommitIndex(),
                                            request.getEntries(request.getEntriesCount() - 1).getIndex()));
        }

        return AppendEntriesResponse.newBuilder()
                                    .setGroupId(groupId())
                                    .setTerm(currentTerm())
                                    .setSuccess(buildAppendEntrySuccess(lastLogIndex()))
                                    .setTerm(currentTerm())
                                    .build();
    }

    @Override
    public RequestVoteResponse requestVote(RequestVoteRequest request) {
        long elapsedFromLastMessage = currentTimeMillis() - lastMessageReceivedAt;

        lastMessageReceivedAt = currentTimeMillis();
        rescheduleElection();

        // If a server receives a RequestVote within the minimum election timeout of hearing from a current leader, it
        // does not update its term or grant its vote
        if (elapsedFromLastMessage < minElectionTimeout()) {
            return requestVoteResponse(false);
        }

        updateCurrentTerm(request.getTerm());
        return requestVoteResponse(voteGrantedFor(request));
    }

    @Override
    public InstallSnapshotResponse installSnapshot(InstallSnapshotRequest request) {
        newMessageReceived(request.getTerm());
        throw new NotImplementedException();
    }

    private void cancelCurrentElectionTimeout() {
        if (scheduledElection != null) {
            scheduledElection.cancel(true);
        }
    }

    private void scheduleNewElection() {
        scheduledElection = scheduledExecutorService.schedule(
                () -> changeStateTo(stateFactory().candidateState()),
                // TODO: make ThreadLocalRandom injectable
                ThreadLocalRandom.current().nextLong(minElectionTimeout(), maxElectionTimeout() + 1),
                TimeUnit.MILLISECONDS);
    }

    private void rescheduleElection() {
        cancelCurrentElectionTimeout();
        scheduleNewElection();
    }

    private void newMessageReceived(long term) {
        lastMessageReceivedAt = currentTimeMillis();
        updateCurrentTerm(term);
        rescheduleElection();
    }


    private AppendEntrySuccess buildAppendEntrySuccess(long lastLogIndex) {
        return AppendEntrySuccess.newBuilder()
                                 .setLastLogIndex(lastLogIndex)
                                 .build();
    }

    private boolean voteGrantedFor(RequestVoteRequest request) {
        //1. Reply false if term < currentTerm
        if (request.getTerm() < currentTerm()) {
            return false;
        }

        //2. If votedFor is null or candidateId, and candidate's log is at least as up-to-date as receiver's log, grant
        // vote
        String votedFor = votedFor();
        if (votedFor != null && !votedFor.equals(request.getCandidateId())) {
            return false;
        }

        if (request.getLastLogTerm() < lastLogTerm()) {
            return false;
        }

        if (request.getLastLogIndex() < lastLogIndex()) {
            return false;
        }

        markVotedFor(request.getCandidateId());
        return true;
    }

    public static class Builder extends AbstractMembershipState.Builder<Builder> {

        private ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();

        public Builder scheduledExecutorService(ScheduledExecutorService scheduledExecutorService) {
            this.scheduledExecutorService = scheduledExecutorService;
            return this;
        }

        public FollowerState build() {
            return new FollowerState(this);
        }
    }
}
