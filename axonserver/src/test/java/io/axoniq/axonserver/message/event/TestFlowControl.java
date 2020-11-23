package io.axoniq.axonserver.message.event;

import com.google.protobuf.ByteString;
import io.axoniq.axonserver.grpc.SerializedObject;
import io.axoniq.axonserver.grpc.event.Event;
import io.axoniq.axonserver.grpc.event.EventStoreGrpc;
import io.axoniq.axonserver.grpc.event.GetAggregateEventsRequest;
import io.grpc.Channel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import org.apache.commons.lang.RandomStringUtils;
import org.junit.*;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Marc Gathier
 */
public class TestFlowControl {

    @Test
    @Ignore
    void flowControl() throws IOException, InterruptedException, TimeoutException, ExecutionException {
        EventStoreGrpc.EventStoreImplBase flowControlledEventStore = new EventStoreGrpc.EventStoreImplBase() {
            @Override
            public void listAggregateEvents(GetAggregateEventsRequest request, StreamObserver<Event> responseObserver) {
                final ServerCallStreamObserver<Event> serverCallStreamObserver =
                        (ServerCallStreamObserver<Event>) responseObserver;
                EventProducer eventProducer = new EventProducer(serverCallStreamObserver, request);


                final OnReadyHandler onReadyHandler = new OnReadyHandler(serverCallStreamObserver, eventProducer);
                serverCallStreamObserver.setOnReadyHandler(onReadyHandler);
                eventProducer.setOnReadyHandler(onReadyHandler);
            }
        };

        EventStoreGrpc.EventStoreImplBase routerEventStore = new EventStoreGrpc.EventStoreImplBase() {
            @Override
            public void listAggregateEvents(GetAggregateEventsRequest request, StreamObserver<Event> responseObserver) {
                ServerCallStreamObserver<Event> callStreamObserver = (ServerCallStreamObserver<Event>) responseObserver;
                Channel channel = ManagedChannelBuilder.forAddress("localhost", 9999).usePlaintext().build();
                EventStoreGrpc.EventStoreStub stub = EventStoreGrpc.newStub(channel);
                stub.listAggregateEvents(request, new ClientResponseObserver<GetAggregateEventsRequest, Event>() {
                    private ClientCallStreamObserver<GetAggregateEventsRequest> requestStream;
                    private Deque<Event> buffer = new ArrayDeque<>();


                    @Override
                    public void onNext(Event value) {
                        if (callStreamObserver.isReady()) {
                            responseObserver.onNext(value);
                            requestStream.request(1);
                        } else {
                            buffer.addLast(value);
                            System.out.println("Intermediate not ready: " + value.getAggregateSequenceNumber());
                        }
                    }

                    @Override
                    public void onError(Throwable t) {
                        responseObserver.onError(t);
                    }

                    @Override
                    public void onCompleted() {
                        responseObserver.onCompleted();
                    }

                    @Override
                    public void beforeStart(ClientCallStreamObserver<GetAggregateEventsRequest> requestStream) {
                        this.requestStream = requestStream;
                        requestStream.disableAutoInboundFlowControl();
                        callStreamObserver.setOnReadyHandler(() -> {
                            while (!buffer.isEmpty()) {
                                System.out.println("Sending buffered item");
                                responseObserver.onNext(buffer.pollFirst());
                            }
                            requestStream.request(1);
                        });
                    }
                });
            }
        };

        Server server = ServerBuilder.forPort(9999).addService(flowControlledEventStore).build();
        server.start();

        Server server2 = ServerBuilder.forPort(9998).addService(routerEventStore).build();
        server2.start();

        Channel channel = ManagedChannelBuilder.forAddress("localhost", 9999).usePlaintext().build();
        EventStoreGrpc.EventStoreStub stub = EventStoreGrpc.newStub(channel);

        AtomicLong lastSequenceNumber = new AtomicLong();
        CompletableFuture<Void> done = new CompletableFuture<>();
        stub.listAggregateEvents(GetAggregateEventsRequest.newBuilder()
                                                          .setAggregateId("Aggregate-375")
                                                          .build(),
                                 new ClientResponseObserver<GetAggregateEventsRequest, Event>() {
                                     private ClientCallStreamObserver requestStream;

                                     @Override
                                     public void beforeStart(ClientCallStreamObserver responseStream) {
                                         this.requestStream = responseStream;
//                responseStream.disableAutoInboundFlowControl();
                                     }

                                     @Override
                                     public void onNext(Event event) {
                                         try {
                                             Thread.sleep(1, 5000);
                                         } catch (InterruptedException e) {
                                             e.printStackTrace();
                                         }
                                         lastSequenceNumber.set(event.getAggregateSequenceNumber());
//                requestStream.request(1);
                                     }

                                     @Override
                                     public void onError(Throwable throwable) {
                                         done.completeExceptionally(throwable);
                                     }

                                     @Override
                                     public void onCompleted() {
                                         done.complete(null);
                                     }
                                 });
        done.get(100, TimeUnit.SECONDS);
        System.out.println("Last sequence number: " + lastSequenceNumber.get());
    }

    private class OnReadyHandler implements Runnable {

        private final ServerCallStreamObserver<Event> serverCallStreamObserver;
        private final EventProducer eventProducer;
        // Guard against spurious onReady() calls caused by a race between onNext() and onReady(). If the transport
        // toggles isReady() from false to true while onNext() is executing, but before onNext() checks isReady(),
        // request(1) would be called twice - once by onNext() and once by the onReady() scheduled during onNext()'s
        // execution.
        private final AtomicBoolean wasReady = new AtomicBoolean();
        private final AtomicLong count = new AtomicLong();

        public OnReadyHandler(ServerCallStreamObserver<Event> serverCallStreamObserver, EventProducer eventProducer) {

            this.serverCallStreamObserver = serverCallStreamObserver;
            this.eventProducer = eventProducer;
        }

        @Override
        public void run() {
            System.out.println("Ready again");
            if (serverCallStreamObserver.isReady() && wasReady.compareAndSet(false, true)) {
                count.incrementAndGet();
                eventProducer.start();
            }
        }
    }

    private class EventProducer {

        private final ServerCallStreamObserver<Event> serverCallStreamObserver;
        private final GetAggregateEventsRequest request;
        private final AtomicLong sequenceNumber = new AtomicLong();
        private OnReadyHandler onReadyHandler;

        public EventProducer(ServerCallStreamObserver<Event> serverCallStreamObserver,
                             GetAggregateEventsRequest request) {
            this.serverCallStreamObserver = serverCallStreamObserver;
            this.request = request;
        }


        public void start() {
            int runs = 10_000;
            long bytes = 0;
            while (serverCallStreamObserver.isReady() && sequenceNumber.get() < runs) {
                Event event = Event.newBuilder()
                                   .setAggregateIdentifier(request.getAggregateId())
                                   .setMessageIdentifier(UUID.randomUUID().toString())
                                   .setAggregateSequenceNumber(sequenceNumber.getAndIncrement())
                                   .setPayload(SerializedObject.newBuilder()
                                                               .setData(ByteString
                                                                                .copyFromUtf8(string(
                                                                                        5000)))
                                                               .build())
                                   .build();
                serverCallStreamObserver.onNext(event);
                bytes += event.getSerializedSize();
            }
            if (sequenceNumber.get() >= runs) {
                serverCallStreamObserver.onCompleted();
                return;
            }
            System.out.println(
                    "Stopped sending as not ready, bytes sent is " + bytes + " next seqnr: " + sequenceNumber.get());
            onReadyHandler.wasReady.set(false);
        }

        private String string(int i) {
            return RandomStringUtils.random(i);
        }

        public void setOnReadyHandler(OnReadyHandler onReadyHandler) {
            this.onReadyHandler = onReadyHandler;
        }
    }
}
