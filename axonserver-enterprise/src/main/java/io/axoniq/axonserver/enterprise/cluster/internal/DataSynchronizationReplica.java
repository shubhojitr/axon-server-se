package io.axoniq.axonserver.enterprise.cluster.internal;

import io.axoniq.axonserver.config.FlowControl;
import io.axoniq.axonserver.config.MessagingPlatformConfiguration;
import io.axoniq.axonserver.enterprise.cluster.ClusterController;
import io.axoniq.axonserver.enterprise.cluster.SafepointRepository;
import io.axoniq.axonserver.enterprise.cluster.events.ClusterEvents;
import io.axoniq.axonserver.enterprise.cluster.events.ContextEvents;
import io.axoniq.axonserver.enterprise.jpa.Safepoint;
import io.axoniq.axonserver.exception.ErrorCode;
import io.axoniq.axonserver.exception.MessagingPlatformException;
import io.axoniq.axonserver.grpc.Confirmation;
import io.axoniq.axonserver.grpc.DataSychronizationServiceInterface;
import io.axoniq.axonserver.grpc.ReceivingStreamObserver;
import io.axoniq.axonserver.grpc.SendingStreamObserver;
import io.axoniq.axonserver.grpc.internal.Permits;
import io.axoniq.axonserver.grpc.internal.SafepointMessage;
import io.axoniq.axonserver.grpc.internal.StartSynchronization;
import io.axoniq.axonserver.grpc.internal.SynchronizationReplicaInbound;
import io.axoniq.axonserver.grpc.internal.SynchronizationReplicaOutbound;
import io.axoniq.axonserver.grpc.internal.TransactionConfirmation;
import io.axoniq.axonserver.grpc.internal.TransactionWithToken;
import io.axoniq.axonserver.localstorage.EventType;
import io.axoniq.axonserver.localstorage.LocalEventStore;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Controller;

import java.time.Clock;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Author: marc
 */
@Controller
public class DataSynchronizationReplica {

    public static final SynchronizationReplicaOutbound SAFEPOINT_CONFIRMATION = SynchronizationReplicaOutbound.newBuilder().setSafepointConfirmation(
            Confirmation.newBuilder().setSuccess(true).build()).build();
    private final ClusterController clusterController;
    private final Logger logger = LoggerFactory.getLogger(DataSynchronizationReplica.class);

    private final Map<String, ReplicaConnection> connectionPerContext = new ConcurrentHashMap<>();
    private final MessagingPlatformConfiguration messagingPlatformConfiguration;
    private final StubFactory stubFactory;
    private final LocalEventStore localEventStore;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final SyncStatusController safepointRepository;
    private final Clock clock;

    public DataSynchronizationReplica(ClusterController clusterController,
                                      MessagingPlatformConfiguration messagingPlatformConfiguration,
                                      StubFactory stubFactory,
                                      LocalEventStore localEventStore,
                                      ApplicationEventPublisher applicationEventPublisher,
                                      SyncStatusController safepointRepository, Clock clock) {
        this.clusterController = clusterController;
        this.messagingPlatformConfiguration = messagingPlatformConfiguration;
        this.stubFactory = stubFactory;
        this.localEventStore = localEventStore;
        this.applicationEventPublisher = applicationEventPublisher;
        this.safepointRepository = safepointRepository;
        this.clock = clock;
    }

    @EventListener
    public synchronized void on(ClusterEvents.MasterConfirmation masterConfirmation) {
        if( clusterController.getName().equals(masterConfirmation.getNode())) return;
        if(  clusterController.getMe().hasStorageContext(masterConfirmation.getContext())) {

            ReplicaConnection old = connectionPerContext.remove(masterConfirmation.getContext());
            if( old != null) {
                logger.debug("{}: old master: {}", masterConfirmation.getContext(), old.node);
                if( old.node.equals(masterConfirmation.getNode())) {
                    connectionPerContext.put(masterConfirmation.getContext(), old);
                    return;
                }
                old.complete();
            }

            logger.info("{}: received master {}", masterConfirmation.getContext(), masterConfirmation.getNode());

            ReplicaConnection replicaConnection = new ReplicaConnection(
                    masterConfirmation.getNode(),
                    masterConfirmation.getContext());
            connectionPerContext.put(masterConfirmation.getContext(), replicaConnection);
            replicaConnection.start();
        }

    }

    @EventListener
    public void on(ClusterEvents.MasterStepDown masterStepDown) {
        ReplicaConnection old = connectionPerContext.remove(masterStepDown.getContextName());
        if( old != null) old.complete();
    }

    @EventListener
    public void on(ContextEvents.ContextDeleted contextDeleted) {
        ReplicaConnection old = connectionPerContext.remove(contextDeleted.getName());
        if( old != null) old.complete();
    }


    @EventListener
    public void on(ContextEvents.NodeRolesUpdated nodeRolesUpdated) {
        if( nodeRolesUpdated.getNode().getName().equals(messagingPlatformConfiguration.getName()) &&
            !nodeRolesUpdated.getNode().isStorage()) {
            ReplicaConnection old = connectionPerContext.remove(nodeRolesUpdated.getName());
            if( old != null) old.complete();
        }
    }

    @EventListener
    public void on(ClusterEvents.MasterDisconnected masterStepDown) {
        ReplicaConnection old = connectionPerContext.remove(masterStepDown.getContextName());
        if( old != null) old.complete();
    }

    @Scheduled(fixedRateString = "${axoniq.axonserver.replication-check-rate:1000}", initialDelayString = "${axoniq.axonserver.replication-check-delay:10000}")
    public void checkAlive() {
        Set<String> invalidConnections = new HashSet<>();
        connectionPerContext.forEach((context, replicaConnection) -> {
            if( !replicaConnection.isAlive()) {
                invalidConnections.add(context);
            }
        });

        invalidConnections.forEach(context -> {
            ReplicaConnection old = connectionPerContext.remove(context);
            if( old != null) {
                old.error("No longer alive");
                applicationEventPublisher.publishEvent(new ClusterEvents.MasterDisconnected(context, false));
            }
        });
    }

    Map<String, ReplicaConnection> getConnectionPerContext() {
        return connectionPerContext;
    }

    class ReplicaConnection {

        private final String node;
        private final String context;
        private final AtomicLong expectedEventToken = new AtomicLong();
        private final AtomicLong expectedSnapshotToken = new AtomicLong();
        private StreamObserver<SynchronizationReplicaOutbound> streamObserver;
        private final ConcurrentNavigableMap<Long, TransactionWithToken> eventsToSynchronize = new ConcurrentSkipListMap<>();
        private final ConcurrentNavigableMap<Long, TransactionWithToken> snapshotsToSynchronize = new ConcurrentSkipListMap<>();
        private final AtomicLong permitsLeft = new AtomicLong();
        private final FlowControl flowControl;
        private volatile long lastEventReceived = System.currentTimeMillis();
        private volatile long lastSnapshotReceived = System.currentTimeMillis();
        private volatile long lastMessageReceived = System.currentTimeMillis();

        public ReplicaConnection(String node, String context) {
            this.node = node;
            this.context = context;
            this.flowControl = messagingPlatformConfiguration.getCommandFlowControl();
        }

        public void start() {
            DataSychronizationServiceInterface stub = stubFactory.dataSynchronizationServiceStub(
                    messagingPlatformConfiguration,
                    clusterController.getNode(node));
            logger.info("{}: starting replication with {}", context, node);

            this.streamObserver = new SendingStreamObserver<>(stub.openConnection(new ReceivingStreamObserver<SynchronizationReplicaInbound>(logger) {
                @Override
                protected void consume(SynchronizationReplicaInbound synchronizationReplicaInbound) {
                    switch (synchronizationReplicaInbound.getRequestCase()) {
                        case EVENT:
                            TransactionWithToken eventRequest = synchronizationReplicaInbound
                                    .getEvent();
                            lastEventReceived = clock.millis();
                            lastMessageReceived = lastEventReceived;
                            syncTransaction(eventRequest, EventType.EVENT, expectedEventToken, eventsToSynchronize);
                            break;
                        case SNAPSHOT:
                            TransactionWithToken snapshotRequest = synchronizationReplicaInbound
                                    .getSnapshot();
                            lastSnapshotReceived = clock.millis();
                            lastMessageReceived = lastSnapshotReceived;
                            syncTransaction(snapshotRequest, EventType.SNAPSHOT, expectedSnapshotToken, snapshotsToSynchronize);

                            break;
                        case SAFEPOINT:
                            SafepointMessage safepoint = synchronizationReplicaInbound
                                    .getSafepoint();
                            lastMessageReceived = clock.millis();
                            safepointRepository.storeSafePoint(EventType.valueOf(safepoint.getType()), safepoint.getContext(), safepoint.getToken());
                            streamObserver.onNext(SAFEPOINT_CONFIRMATION);
                            break;
                        case REQUEST_NOT_SET:
                            break;
                    }
                }

                @Override
                protected String sender() {
                    return node;
                }

                @Override
                public void onError(Throwable cause) {
                    ManagedChannelHelper.checkShutdownNeeded(node, cause);
                    logger.warn("Received error from {}: {}", node, cause.getMessage());
                    applicationEventPublisher.publishEvent(new ClusterEvents.MasterDisconnected(context, false));
                }

                @Override
                public void onCompleted() {
                    logger.debug("Received close from {}", node);
                    DataSynchronizationReplica.this.connectionPerContext.remove(context);
                }
            }));

            expectedEventToken.set(localEventStore.getLastToken(context)+1);
            expectedSnapshotToken.set(localEventStore.getLastSnapshot(context)+1);
            streamObserver.onNext(SynchronizationReplicaOutbound.newBuilder()
                                                                .setStart(StartSynchronization.newBuilder()
                                                                                              .setContext(context)
                                                                                              .setNodeName(messagingPlatformConfiguration.getName())
                                                                                              .setEventToken(localEventStore.getLastToken(context)+1)
                                                                                              .setSnaphshotToken(localEventStore.getLastSnapshot(context)+1)
                                                                                              .setPermits(flowControl.getInitialPermits())
                                                                                              .build())
                                                                .build());

            permitsLeft.set(flowControl.getInitialPermits() - flowControl.getThreshold());

        }

        private boolean isAlive() {
            if( lastEventReceived < clock.millis() - TimeUnit.SECONDS.toMillis(10) && isProcessingBacklog(EventType.EVENT, expectedEventToken)) {
                logger.warn("{}: Not received any events while processing backlog (waiting for: {}, safepoint: {})", context, expectedEventToken,
                            safepointRepository.getSafePoint(EventType.EVENT, context));
                return false;
            }
            if( lastMessageReceived < clock.millis() - TimeUnit.SECONDS.toMillis(20)) {
                logger.warn("{}: Not received any messages", context);
                return false;
            }
            return true;
        }

        private boolean isProcessingBacklog(EventType eventType, AtomicLong expectedToken) {
            return safepointRepository.getSafePoint(eventType, context) > expectedToken.get();
        }

        private void syncTransaction(TransactionWithToken syncRequest, EventType type, AtomicLong expectedToken,
                                     ConcurrentNavigableMap<Long, TransactionWithToken> waitingToSynchronize) {
            if (syncRequest.getToken() < expectedToken.get()) {
                if( contains(type, syncRequest)) {
                    messageProcessed(true, expectedToken.get(), type.name());
                    return;
                }

                rollback(type, syncRequest);
                expectedToken.set(syncRequest.getToken());
            }

            safepointRepository.storeSafePoint(type, context, syncRequest.getSafePoint());
            waitingToSynchronize.put(syncRequest.getToken(), syncRequest);
            Map.Entry<Long, TransactionWithToken> head = waitingToSynchronize
                    .pollFirstEntry();
            while (head != null && head.getKey().equals(expectedToken
                                                                                .get())) {
                expectedToken.set(syncTransaction(syncRequest, type));
                safepointRepository.updateGeneration(type, context, head.getValue().getMasterGeneration());

                head = waitingToSynchronize.pollFirstEntry();
            }
            if (head != null) waitingToSynchronize.put(head.getKey(),
                                                                 head.getValue());
        }

        private void rollback(EventType eventType, TransactionWithToken syncRequest) {
            if( EventType.EVENT.equals(eventType)) {
                localEventStore.rollbackEvents(context, syncRequest.getToken());
            } else {
                localEventStore.rollbackSnapshots(context, syncRequest.getToken());
            }

        }

        private boolean contains(EventType eventType, TransactionWithToken syncRequest) {
            if( EventType.EVENT.equals(eventType)) {
                return localEventStore.containsEvents(context, syncRequest);
            } else {
                return localEventStore.containsSnapshots(context, syncRequest);
            }
        }

        private long syncTransaction(TransactionWithToken transactionWithToken, EventType eventType) {
            if( logger.isTraceEnabled()) logger.trace("Writing transaction: {}, # events: {}", transactionWithToken.getToken(), transactionWithToken.getEventsCount());
            long expected;
            if( EventType.EVENT.equals(eventType)) {
                expected = localEventStore.syncEvents(context, transactionWithToken);
            } else {
                expected = localEventStore.syncSnapshots(context, transactionWithToken);
            }

            messageProcessed(safepointRepository.getSafePoint(eventType, context) < expected, transactionWithToken.getToken(), eventType.name());
            return expected;
        }

        private void messageProcessed(boolean sendConfirmation, long nextExpectedToken, String eventTypeName) {
            if (sendConfirmation) {
                TransactionConfirmation request = TransactionConfirmation.newBuilder()
                                                                         .setToken(nextExpectedToken)
                                                                         .setType(eventTypeName)
                                                                         .build();
                streamObserver.onNext(SynchronizationReplicaOutbound.newBuilder().setConfirmation(request)
                                                                             .build());
            }

            markConsumed();
        }

        private void markConsumed() {
            permitsLeft.decrementAndGet();
            if( permitsLeft.compareAndSet(0, flowControl.getNewPermits())) {
                logger.info("Granting new {} permits", flowControl.getNewPermits());
                streamObserver.onNext(SynchronizationReplicaOutbound.newBuilder().setPermits(Permits.newBuilder().setPermits(flowControl.getNewPermits()).build()).build());
            }

        }

        public void complete() {
            try {
                expectedSnapshotToken.set(Long.MAX_VALUE);
                expectedEventToken.set(Long.MAX_VALUE);
                streamObserver.onCompleted();
            } catch (RuntimeException cause) {
                logger.debug("{}: Failed to complete with error", context, cause);
            }
        }

        public String getNode() {
            return node;
        }

        public long getExpectedEventToken() {
            return expectedEventToken.get();
        }

        public long getExpectedSnapshotToken() {
            return expectedSnapshotToken.get();
        }

        public int waitingEvents() {
            return eventsToSynchronize.size();
        }
        public int waitingSnapshots() {
            return snapshotsToSynchronize.size();
        }

        public void error(String message) {
            try {
                expectedSnapshotToken.set(Long.MAX_VALUE);
                expectedEventToken.set(Long.MAX_VALUE);
                streamObserver.onError(new MessagingPlatformException(ErrorCode.OTHER, message));
            } catch (RuntimeException cause) {
                logger.debug("{}: Failed to complete with error", context, cause);
            }
        }
    }
}