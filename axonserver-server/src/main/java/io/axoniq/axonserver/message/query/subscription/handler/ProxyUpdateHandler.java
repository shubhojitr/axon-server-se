package io.axoniq.axonserver.message.query.subscription.handler;

import io.axoniq.axonhub.SubscriptionQueryResponse;
import io.axoniq.axonserver.grpc.Publisher;
import io.axoniq.axonhub.internal.grpc.ConnectorCommand;
import io.axoniq.axonserver.message.query.subscription.UpdateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.axoniq.axonhub.internal.grpc.ConnectorCommand.newBuilder;

/**
 * Created by Sara Pellegrini on 03/05/2018.
 * sara.pellegrini@gmail.com
 */
public class ProxyUpdateHandler implements UpdateHandler {

    private final Logger logger = LoggerFactory.getLogger(ProxyUpdateHandler.class);
    private final Publisher<ConnectorCommand> destination;

    public ProxyUpdateHandler(Publisher<ConnectorCommand> destination) {
        this.destination = destination;
    }

    @Override
    public void onSubscriptionQueryResponse(SubscriptionQueryResponse response) {
        logger.debug("SubscriptionQueryResponse for subscription Id {} send to proxy.",
                     response.getSubscriptionIdentifier());
        destination.publish(newBuilder().setSubscriptionQueryResponse(response).build());
    }

}