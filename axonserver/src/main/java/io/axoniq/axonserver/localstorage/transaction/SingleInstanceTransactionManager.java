package io.axoniq.axonserver.localstorage.transaction;

import io.axoniq.axonserver.grpc.event.Event;
import io.axoniq.axonserver.localstorage.EventStore;
import io.axoniq.axonserver.localstorage.StorageCallback;

import java.util.List;

/**
 * Author: marc
 */
public class SingleInstanceTransactionManager implements StorageTransactionManager{
    private final EventStore datafileManagerChain;

    public SingleInstanceTransactionManager(
            EventStore datafileManagerChain) {
        this.datafileManagerChain = datafileManagerChain;
    }

    @Override
    public void store(List<Event> eventList, StorageCallback storageCallback) {
        datafileManagerChain.store(datafileManagerChain.prepareTransaction(eventList), storageCallback);
    }

    @Override
    public long getLastToken() {
        return datafileManagerChain.getLastToken();
    }

    @Override
    public void reserveSequenceNumbers(List<Event> eventList) {
        datafileManagerChain.reserveSequenceNumbers(eventList);
    }
}
