package io.axoniq.axonserver.enterprise.logconsumer;

import com.google.protobuf.InvalidProtocolBufferException;
import io.axoniq.axonserver.enterprise.component.processor.balancing.stategy.LoadBalanceStrategyController;
import io.axoniq.axonserver.grpc.cluster.Entry;
import io.axoniq.axonserver.grpc.internal.LoadBalanceStrategy;
import org.springframework.stereotype.Component;

/**
 * @author Marc Gathier
 */
@Component
public class DeleteLoadBalancingStrategyConsumer implements LogEntryConsumer {
    public static final String DELETE_LOAD_BALANCING_STRATEGY ="DELETE_LOAD_BALANCING_STRATEGY";
    private final LoadBalanceStrategyController loadBalanceStrategyController;

    public DeleteLoadBalancingStrategyConsumer(
            LoadBalanceStrategyController loadBalanceStrategyController) {
        this.loadBalanceStrategyController = loadBalanceStrategyController;
    }

    @Override
    public void consumeLogEntry(String groupId, Entry entry) {
        if( entryType(entry, DELETE_LOAD_BALANCING_STRATEGY)) {
            try {
                LoadBalanceStrategy strategy = LoadBalanceStrategy.parseFrom(entry.getSerializedObject().getData());
                loadBalanceStrategyController.delete(strategy.getName());
            } catch (InvalidProtocolBufferException e) {
                e.printStackTrace();
            }
        }
    }

}
