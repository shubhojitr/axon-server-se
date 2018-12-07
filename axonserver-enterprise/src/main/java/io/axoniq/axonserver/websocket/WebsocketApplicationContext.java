package io.axoniq.axonserver.websocket;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Created by Sara Pellegrini on 27/03/2018.
 * sara.pellegrini@gmail.com
 */
@Service
public class WebsocketApplicationContext {

    private final SimpMessagingTemplate websocket;

    public WebsocketApplicationContext(SimpMessagingTemplate websocket) {
        this.websocket = websocket;
    }


}
