package io.axoniq.axonserver.enterprise.logconsumer;


import io.axoniq.axonserver.enterprise.ContextEvents;
import io.axoniq.axonserver.enterprise.cluster.GrpcRaftController;
import io.axoniq.axonserver.enterprise.context.ContextController;
import io.axoniq.axonserver.grpc.cluster.Entry;
import io.axoniq.axonserver.grpc.internal.ContextConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Author: marc
 */
@Component
public class AdminConfigConsumer implements LogEntryConsumer {
    private Logger logger = LoggerFactory.getLogger(AdminConfigConsumer.class);

    private final ContextController contextController;
    private final ApplicationEventPublisher eventPublisher;

    public AdminConfigConsumer(ContextController contextController,
                               ApplicationEventPublisher eventPublisher) {
        this.contextController = contextController;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void consumeLogEntry(String groupId, Entry e) {
        if( groupId.equals(GrpcRaftController.ADMIN_GROUP) && entryType(e, ContextConfiguration.class)) {
                try {
                    ContextConfiguration contextConfiguration = ContextConfiguration.parseFrom(e.getSerializedObject().getData());
                    logger.warn("{}: received data: {}", groupId, contextConfiguration);
                    contextController.updateContext(contextConfiguration);
                    eventPublisher.publishEvent(new ContextEvents.ContextUpdated(groupId));
                } catch (Exception e1) {
                    logger.warn("{}: Failed to process log entry: {}", groupId, e, e1);
                }
        }
    }

    @Override
    public int priority() {
        return 0;
    }
}
