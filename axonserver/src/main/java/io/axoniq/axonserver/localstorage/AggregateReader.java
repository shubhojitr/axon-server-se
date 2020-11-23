/*
 * Copyright (c) 2017-2019 AxonIQ B.V. and/or licensed to AxonIQ B.V.
 * under one or more contributor license agreements.
 *
 *  Licensed under the AxonIQ Open Source License Agreement v1.0;
 *  you may not use this file except in compliance with the license.
 *
 */

package io.axoniq.axonserver.localstorage;

import io.axoniq.axonserver.localstorage.file.EventStreamReadyHandler;
import io.grpc.stub.CallStreamObserver;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * @author Marc Gathier
 */
public class AggregateReader {

    private final EventStorageEngine eventStorageEngine;
    private final SnapshotReader snapshotReader;

    public AggregateReader(EventStorageEngine eventStorageEngine, SnapshotReader snapshotReader) {
        this.eventStorageEngine = eventStorageEngine;
        this.snapshotReader = snapshotReader;
    }

    public void readEvents(String aggregateId, boolean useSnapshots, long minSequenceNumber,
                           Consumer<SerializedEvent> eventConsumer) {
        SerializedEventCallStreamObserver callStreamObserver = new SerializedEventCallStreamObserver(eventConsumer);
        readEvents(aggregateId,
                   useSnapshots,
                   minSequenceNumber,
                   Long.MAX_VALUE,
                   0,
                   callStreamObserver, new EventStreamReadyHandler(callStreamObserver));
    }

    public void readEvents(String aggregateId, boolean useSnapshots, long minSequenceNumber, long maxSequenceNumber,
                           long minToken,
                           Consumer<SerializedEvent> eventConsumer) {
        SerializedEventCallStreamObserver callStreamObserver = new SerializedEventCallStreamObserver(eventConsumer);
        readEvents(aggregateId,
                   useSnapshots,
                   minSequenceNumber,
                   maxSequenceNumber,
                   minToken,
                   callStreamObserver, new EventStreamReadyHandler(callStreamObserver));
    }

    public void readEvents(String aggregateId, boolean useSnapshots, long minSequenceNumber, long maxSequenceNumber,
                           long minToken,
                           CallStreamObserver<SerializedEvent> eventConsumer,
                           EventStreamReadyHandler eventStreamReadyHandler) {
        long actualMinSequenceNumber = minSequenceNumber;
        if (useSnapshots) {
            Optional<SerializedEvent> snapshot = snapshotReader.readSnapshot(aggregateId, minSequenceNumber);
            if (snapshot.isPresent()) {
                eventConsumer.onNext(snapshot.get());
                if (snapshot.get().getAggregateSequenceNumber() >= maxSequenceNumber) {
                    return;
                }
                actualMinSequenceNumber = snapshot.get().asEvent().getAggregateSequenceNumber() + 1;
            }
        }
        eventStorageEngine.processEventsPerAggregate(aggregateId,
                                                     actualMinSequenceNumber,
                                                     maxSequenceNumber,
                                                     minToken,
                                                     eventConsumer,
                                                     eventStreamReadyHandler);
    }

    public void readSnapshots(String aggregateId, long minSequenceNumber, long maxSequenceNumber, int maxResults,
                              CallStreamObserver<SerializedEvent> eventConsumer) {
        snapshotReader.streamByAggregateId(aggregateId, minSequenceNumber, maxSequenceNumber,
                                           maxResults > 0 ? maxResults : Integer.MAX_VALUE, eventConsumer);
    }

    public long readHighestSequenceNr(String aggregateId) {
        return eventStorageEngine.getLastSequenceNumber(aggregateId).orElse(-1L);
    }

    public long readHighestSequenceNr(String aggregateId, int maxSegmentsHint, long maxTokenHint) {
        return eventStorageEngine.getLastSequenceNumber(aggregateId, maxSegmentsHint, maxTokenHint).orElse(-1L);
    }

    public static class SerializedEventCallStreamObserver extends CallStreamObserver<SerializedEvent> {

        private final Consumer<SerializedEvent> eventConsumer;
        private Runnable runnable;

        public SerializedEventCallStreamObserver(Consumer<SerializedEvent> eventConsumer) {
            this.eventConsumer = eventConsumer;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setOnReadyHandler(Runnable runnable) {
            this.runnable = runnable;
        }

        @Override
        public void disableAutoInboundFlowControl() {
        }

        @Override
        public void request(int i) {
        }

        @Override
        public void setMessageCompression(boolean b) {
        }

        @Override
        public void onNext(SerializedEvent serializedEvent) {
            eventConsumer.accept(serializedEvent);
        }

        @Override
        public void onError(Throwable throwable) {
        }

        @Override
        public void onCompleted() {
        }

        public void ready() {
            runnable.run();
        }
    }
}
