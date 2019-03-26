package io.axoniq.axonserver.localstorage;

import io.axoniq.axonserver.grpc.event.EventWithToken;
import org.springframework.boot.actuate.health.Health;

import java.util.Iterator;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * @author Marc Gathier
 */
public class EventStreamReader {
    private final EventStorageEngine eventStorageEngine;
    private final EventWriteStorage eventWriteStorage;

    public EventStreamReader(EventStorageEngine datafileManagerChain,
                             EventWriteStorage eventWriteStorage) {
        this.eventStorageEngine = datafileManagerChain;
        this.eventWriteStorage = eventWriteStorage;
    }

    public EventStreamController createController(Consumer<SerializedEventWithToken> eventWithTokenConsumer, Consumer<Throwable> errorCallback) {
        return new EventStreamController(eventWithTokenConsumer, errorCallback, eventStorageEngine, eventWriteStorage);
    }

    public Iterator<SerializedTransactionWithToken> transactionIterator(long firstToken, long limitToken) {
        return eventStorageEngine.transactionIterator(firstToken, limitToken);
    }

    public void query(long minToken, long minTimestamp, Predicate<EventWithToken> consumer) {
        eventStorageEngine.query(minToken, minTimestamp, consumer);
    }

    public long getFirstToken() {
        return eventStorageEngine.getFirstToken();
    }

    public long getTokenAt(long instant) {
        return eventStorageEngine.getTokenAt(instant);
    }

    public void health(Health.Builder builder) {
        eventStorageEngine.health(builder);
    }

    public long getLastToken() {
        return eventStorageEngine.getLastToken();
    }
}
