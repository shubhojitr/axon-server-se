package io.axoniq.axonserver.message.event;

import io.axoniq.axondb.Event;
import io.axoniq.axondb.grpc.Confirmation;
import io.axoniq.axondb.grpc.EventStoreGrpc;
import io.axoniq.axondb.grpc.GetAggregateEventsRequest;
import io.axoniq.axondb.grpc.GetEventsRequest;
import io.axoniq.axondb.grpc.GetFirstTokenRequest;
import io.axoniq.axondb.grpc.GetLastTokenRequest;
import io.axoniq.axondb.grpc.GetTokenAtRequest;
import io.axoniq.axondb.grpc.QueryEventsRequest;
import io.axoniq.axondb.grpc.QueryEventsResponse;
import io.axoniq.axondb.grpc.ReadHighestSequenceNrRequest;
import io.axoniq.axondb.grpc.ReadHighestSequenceNrResponse;
import io.axoniq.axondb.grpc.TrackingToken;
import io.axoniq.axonserver.cluster.jpa.ClusterNode;
import io.axoniq.axonserver.config.MessagingPlatformConfiguration;
import io.axoniq.axonserver.exception.ErrorCode;
import io.axoniq.axonserver.exception.MessagingPlatformException;
import io.axoniq.axonserver.grpc.ManagedChannelHelper;
import io.axoniq.axonserver.grpc.internal.InternalTokenAddingInterceptor;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.stub.AbstractStub;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.StreamObserver;

import java.io.InputStream;
import java.util.concurrent.CompletableFuture;

/**
 * Author: marc
 */
public class RemoteEventStore implements io.axoniq.axonserver.message.event.EventStore {
    private final ClusterNode clusterNode;
    private final MessagingPlatformConfiguration messagingPlatformConfiguration;

    public RemoteEventStore(ClusterNode clusterNode,
                            MessagingPlatformConfiguration messagingPlatformConfiguration) {
        this.clusterNode = clusterNode;
        this.messagingPlatformConfiguration = messagingPlatformConfiguration;
    }

    public EventStoreGrpc.EventStoreStub getEventStoreStub(String context) {
        Channel channel = ManagedChannelHelper.createManagedChannel(messagingPlatformConfiguration, clusterNode);
        if (channel == null) throw new MessagingPlatformException(ErrorCode.NO_EVENTSTORE,
                                                                  "No connection to event store available");
        return EventStoreGrpc.newStub(channel).withInterceptors(
                new ContextAddingInterceptor(() -> context),
                new InternalTokenAddingInterceptor(messagingPlatformConfiguration.getAccesscontrol().getInternalToken()));
    }

    public EventDispatcherStub getNonMarshallingStub(String context) {
        Channel channel = ManagedChannelHelper.createManagedChannel(messagingPlatformConfiguration, clusterNode);
        if (channel == null) throw new MessagingPlatformException(ErrorCode.NO_EVENTSTORE,
                                                                  "No connection to event store available");
        return new EventDispatcherStub(channel).withInterceptors(
                new ContextAddingInterceptor(() -> context),
                new InternalTokenAddingInterceptor(messagingPlatformConfiguration.getAccesscontrol().getInternalToken()));
    }

    @Override
    public CompletableFuture<Confirmation> appendSnapshot(String context, Event eventMessage) {
        EventStoreGrpc.EventStoreStub stub = getEventStoreStub(context);
        CompletableFuture<Confirmation> completableFuture = new CompletableFuture<>();
        stub.appendSnapshot(eventMessage, new CompletableStreamObserver<>(completableFuture));
        return completableFuture;
    }

    @Override
    public StreamObserver<Event> createAppendEventConnection(String context,
                                                                   StreamObserver<Confirmation> responseObserver) {
        EventStoreGrpc.EventStoreStub stub = getEventStoreStub(context);
        return stub.appendEvent(responseObserver);
    }

    @Override
    public void listAggregateEvents(String context, GetAggregateEventsRequest request,
                                    StreamObserver<InputStream> responseStreamObserver) {
        EventDispatcherStub stub = getNonMarshallingStub(context);
        stub.listAggregateEvents(request, responseStreamObserver);

    }

    @Override
    public StreamObserver<GetEventsRequest> listEvents(String context,
                                                       StreamObserver<InputStream> responseStreamObserver) {
        EventDispatcherStub stub = getNonMarshallingStub(context);
        return stub.listEvents(responseStreamObserver);
    }

    @Override
    public void getFirstToken(String context, GetFirstTokenRequest request,
                              StreamObserver<TrackingToken> responseObserver) {
        getEventStoreStub(context).getFirstToken(request, responseObserver);
    }

    @Override
    public void getLastToken(String context, GetLastTokenRequest request,
                             StreamObserver<TrackingToken> responseObserver) {

        getEventStoreStub(context).getLastToken(request, responseObserver);
    }

    @Override
    public void getTokenAt(String context, GetTokenAtRequest request, StreamObserver<TrackingToken> responseObserver) {
        getEventStoreStub(context).getTokenAt(request, responseObserver);
    }

    @Override
    public void readHighestSequenceNr(String context, ReadHighestSequenceNrRequest request,
                                      StreamObserver<ReadHighestSequenceNrResponse> responseObserver) {
        getEventStoreStub(context).readHighestSequenceNr(request, responseObserver);
    }

    @Override
    public StreamObserver<QueryEventsRequest> queryEvents(String context,
                                                          StreamObserver<QueryEventsResponse> responseObserver) {
        return getEventStoreStub(context).queryEvents(responseObserver);
    }

    private static class CompletableStreamObserver<T> implements StreamObserver<T> {

        private final CompletableFuture<T> completableFuture;

        public CompletableStreamObserver(
                CompletableFuture<T> completableFuture) {
            this.completableFuture = completableFuture;
        }

        @Override
        public void onNext(T t) {
            completableFuture.complete(t);
        }

        @Override
        public void onError(Throwable throwable) {
            completableFuture.completeExceptionally(throwable);

        }

        @Override
        public void onCompleted() {
            // no-op
        }
    }

    private static class EventDispatcherStub extends AbstractStub<EventDispatcherStub> {
        protected EventDispatcherStub(Channel channel) {
            super(channel);
        }

        protected EventDispatcherStub(Channel channel, CallOptions callOptions) {
            super(channel, callOptions);
        }

        @Override
        protected EventDispatcherStub build(Channel channel, CallOptions callOptions) {
            return new EventDispatcherStub(channel, callOptions);
        }

        public StreamObserver<GetEventsRequest> listEvents(StreamObserver<InputStream> inputStream) {
            return ClientCalls.asyncBidiStreamingCall(
                    getChannel().newCall(EventDispatcher.METHOD_LIST_EVENTS, getCallOptions()), inputStream);
        }

        public void listAggregateEvents(GetAggregateEventsRequest request, StreamObserver<InputStream> responseStream) {
            ClientCalls.asyncServerStreamingCall(
                    getChannel().newCall(EventDispatcher.METHOD_LIST_AGGREGATE_EVENTS, getCallOptions()), request, responseStream);
        }

    }

}