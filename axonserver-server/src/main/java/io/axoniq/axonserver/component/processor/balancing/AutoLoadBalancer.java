package io.axoniq.axonserver.component.processor.balancing;

import io.axoniq.axonserver.ClusterEvents.ApplicationConnected;
import io.axoniq.axonserver.ClusterEvents.ApplicationDisconnected;
import io.axoniq.axonserver.EventProcessorEvents.EventProcessorStatusUpdated;
import io.axoniq.axonserver.cluster.coordinator.AxonHubManager;
import io.axoniq.axonhub.internal.grpc.ClientEventProcessorStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Created by Sara Pellegrini on 10/08/2018.
 * sara.pellegrini@gmail.com
 */
@Component
public class AutoLoadBalancer {

    private final Consumer<TrackingEventProcessor> balancer;

    private final Function<String,Boolean> coordinatorForContext;

    private final Map<TrackingEventProcessor, Collection<String>> cache = new ConcurrentHashMap<>();

    private final Map<String, String> componentMap = new HashMap<>();

    @Autowired
    public AutoLoadBalancer(UpdatedLoadBalance balancer, AxonHubManager manager) {
        this(balancer::balance, manager::isCoordinatorFor);
    }

    AutoLoadBalancer(Consumer<TrackingEventProcessor> balancer,
                     Function<String, Boolean> coordinatorForContext) {
        this.balancer = balancer;
        this.coordinatorForContext = coordinatorForContext;
    }

    @EventListener
    public void onClientConnected(ApplicationConnected event) {
        componentMap.put(event.getClient(), event.getComponentName());
    }

    @EventListener
    public void onEventProcessorStatusChange(EventProcessorStatusUpdated event) {
        ClientEventProcessorStatus status = event.eventProcessorStatus();
        String context = status.getContext();
        String client = status.getClient();
        if (!componentMap.containsKey(client)) return;
        String component = componentMap.get(client);
        String processor = status.getEventProcessorInfo().getProcessorName();
        TrackingEventProcessor current = new TrackingEventProcessor(processor, component, context);
        Collection<String> clients = cache.computeIfAbsent(current, s -> new LinkedList<>());

        if (!clients.contains(client)) {
            clients.add(client);
            balance(current);
        }
    }

    @EventListener
    public void onClientDisconnected(ApplicationDisconnected event) {
        componentMap.remove(event.getClient());
        cache.forEach((processor, clients) -> {
            boolean removed = clients.remove(event.getClient());
            if (removed){
                balance(processor);
            }
        });
    }

    private void balance(TrackingEventProcessor processor) {
        if (isNotCoordinator(processor.context())) return;
        balancer.accept(processor);
    }

    private boolean isNotCoordinator(String context){
        return !coordinatorForContext.apply(context);
    }

}