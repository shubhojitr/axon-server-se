package io.axoniq.axonserver.component.processor.listener;

import io.axoniq.axonserver.applicationevents.EventProcessorEvents;
import io.axoniq.axonserver.component.processor.ClientEventProcessorInfo;
import io.axoniq.axonserver.grpc.control.EventProcessorInfo;
import org.junit.*;

import java.util.Spliterators;
import java.util.stream.StreamSupport;

import static org.junit.Assert.*;

/**
 * Author: marc
 */
public class ProcessorsInfoTargetTest {
    private ProcessorsInfoTarget testSubject = new ProcessorsInfoTarget();

    @Test
    public void onEventProcessorStatusChange() {
        EventProcessorInfo processorInfo = EventProcessorInfo.newBuilder()
                                                             .setActiveThreads(10)
                                                             .setAvailableThreads(20)
                                                             .setProcessorName("Name")
                                                             .build();
        ClientEventProcessorInfo clientEventProcessorInfo = new ClientEventProcessorInfo("client",
                                                                                         "context",
                                                                                         processorInfo);
        EventProcessorEvents.EventProcessorStatusUpdate event = new EventProcessorEvents.EventProcessorStatusUpdate(
                clientEventProcessorInfo, false);
        EventProcessorEvents.EventProcessorStatusUpdated updatedEvent = testSubject
                .onEventProcessorStatusChange(event);
        assertEquals("client", updatedEvent.eventProcessorStatus().getClientName());
        assertEquals("context", updatedEvent.eventProcessorStatus().getContext());
        ClientProcessor clientProcessor = StreamSupport.stream(Spliterators.spliterator(testSubject.iterator(), 100, 0), false).
                filter(cp -> cp.clientId().equals("client")).findFirst().orElse(null);
        assertNotNull(clientProcessor);
    }

    @Test
    public void onClientConnected() {
    }

    @Test
    public void onClientDisconnected() {
    }

}