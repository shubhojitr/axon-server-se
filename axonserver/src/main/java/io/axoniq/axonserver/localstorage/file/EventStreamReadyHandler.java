/*
 * Copyright (c) 2017-2020 AxonIQ B.V. and/or licensed to AxonIQ B.V.
 * under one or more contributor license agreements.
 *
 *  Licensed under the AxonIQ Open Source License Agreement v1.0;
 *  you may not use this file except in compliance with the license.
 *
 */

package io.axoniq.axonserver.localstorage.file;

import io.axoniq.axonserver.localstorage.SerializedEvent;
import io.grpc.stub.CallStreamObserver;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Marc Gathier
 */
public class EventStreamReadyHandler implements Runnable {

    private final CallStreamObserver<SerializedEvent> eventConsumer;
    private volatile Runnable onReady;
    private final AtomicBoolean wasReady = new AtomicBoolean();

    public EventStreamReadyHandler(CallStreamObserver<SerializedEvent> eventConsumer) {
        this.eventConsumer = eventConsumer;
    }

    @Override
    public void run() {
        System.out.println("READY: " + wasReady + " onReady: " + (onReady != null));
        if (eventConsumer.isReady() && wasReady.compareAndSet(false, true)) {
            if (onReady != null) {
                onReady.run();
            }
        }
    }

    public void setOnReady(Runnable onReady) {
        this.onReady = onReady;
    }

    public void setWasReady(boolean value) {
        wasReady.set(value);
    }
}
