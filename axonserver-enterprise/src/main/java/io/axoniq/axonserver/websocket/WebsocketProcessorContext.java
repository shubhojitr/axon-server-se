package io.axoniq.axonserver.websocket;

import io.axoniq.axonserver.enterprise.cluster.events.ContextEvents;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Created by Sara Pellegrini on 27/03/2018.
 * sara.pellegrini@gmail.com
 */
@Service
public class WebsocketProcessorContext {

    private final SimpMessagingTemplate websocket;

    public WebsocketProcessorContext(SimpMessagingTemplate websocket) {
        this.websocket = websocket;
    }

    @EventListener
    public void on(ContextEvents.BaseContextEvent event) {
        websocket.convertAndSend("/topic/cluster", event.getClass().getName());
    }
}