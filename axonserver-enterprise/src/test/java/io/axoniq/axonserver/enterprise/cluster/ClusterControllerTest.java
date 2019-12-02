package io.axoniq.axonserver.enterprise.cluster;

import io.axoniq.axonserver.AxonServer;
import io.axoniq.axonserver.TestSystemInfoProvider;
import io.axoniq.axonserver.cluster.grpc.LogReplicationService;
import io.axoniq.axonserver.config.AccessControlConfiguration;
import io.axoniq.axonserver.config.FeatureChecker;
import io.axoniq.axonserver.config.MessagingPlatformConfiguration;
import io.axoniq.axonserver.enterprise.cluster.internal.MessagingClusterServiceInterface;
import io.axoniq.axonserver.enterprise.cluster.internal.RemoteConnection;
import io.axoniq.axonserver.enterprise.cluster.internal.StubFactory;
import io.axoniq.axonserver.enterprise.config.ClusterConfiguration;
import io.axoniq.axonserver.enterprise.config.TagsConfiguration;
import io.axoniq.axonserver.enterprise.jpa.ClusterNode;
import io.axoniq.axonserver.enterprise.jpa.Context;
import io.axoniq.axonserver.grpc.ChannelCloser;
import io.axoniq.axonserver.grpc.cluster.Role;
import io.axoniq.axonserver.grpc.internal.NodeInfo;
import io.axoniq.axonserver.licensing.Limits;
import io.axoniq.axonserver.message.command.CommandDispatcher;
import io.axoniq.axonserver.message.query.QueryDispatcher;
import io.axoniq.axonserver.topology.Topology;
import org.junit.*;
import org.junit.runner.*;
import org.mockito.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.persistence.EntityManager;

import static io.axoniq.axonserver.util.AssertUtils.assertWithin;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Marc Gathier
 */
@RunWith(SpringRunner.class)
@DataJpaTest
@Transactional
@ComponentScan(basePackages = "io.axoniq.axonserver.enterprise.jpa", lazyInit = true)
@ContextConfiguration(classes = AxonServer.class)
public class ClusterControllerTest {
    private ClusterController testSubject;
    @Mock
    private NodeSelectionStrategy nodeSelectionStrategy;
    @Mock
    private Limits limits;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private EntityManager entityManager;

    @Mock
    private ChannelCloser channelCloser;

    @MockBean
    private LogReplicationService logReplicationService;
    @MockBean
    private GrpcRaftController raftController;
    private Context context;

    @Autowired
    private ClusterNodeRepository clusterNodeRepository;

    @Before
    public void setUp()  {
        context = new Context(Topology.DEFAULT_CONTEXT);
        entityManager.persist(context);
        FeatureChecker limits = new FeatureChecker() {
            @Override
            public boolean isEnterprise() {
                return true;
            }

            @Override
            public int getMaxClusterSize() {
                return 5;
            }
        };
        ClusterNode clusterNode = new ClusterNode("MyName", "LAPTOP-1QH9GIHL.axoniq.io", "LAPTOP-1QH9GIHL.axoniq.net", 8124, 8224, 8024);
        clusterNode.addContext(context, "MyName", Role.PRIMARY);
        entityManager.persist(clusterNode);

        MessagingPlatformConfiguration messagingPlatformConfiguration = new MessagingPlatformConfiguration(new TestSystemInfoProvider());
        messagingPlatformConfiguration.setAccesscontrol(new AccessControlConfiguration());
        messagingPlatformConfiguration.setName("MyName");

        messagingPlatformConfiguration.setHostname("LAPTOP-1QH9GIHL");
        messagingPlatformConfiguration.setDomain("axoniq.io");
        messagingPlatformConfiguration.setInternalDomain("axoniq.net");

        Map<String,String> tagMap = new HashMap<String,String>(){{put("some","tag");}};

        TagsConfiguration tagsConfiguration = new TagsConfiguration();
        tagsConfiguration.setTags(tagMap);

        ClusterConfiguration clusterConfiguration = new ClusterConfiguration();

        StubFactory stubFactory = new StubFactory() {
            @Override
            public MessagingClusterServiceInterface messagingClusterServiceStub(
                    ClusterNode clusterNode) {
                return new TestMessagingClusterService();
            }

            @Override
            public MessagingClusterServiceInterface messagingClusterServiceStub(
                    String host, int port) {
                return new TestMessagingClusterService();
            }

        };

        when(raftController.isAutoStartup()).thenReturn(false);

        RaftGroupRepositoryManager mockRaftGroupRepositoryManager = mock(RaftGroupRepositoryManager.class);
        CommandDispatcher commandDispatcher = mock(CommandDispatcher.class);
        QueryDispatcher queryDispatcher = mock(QueryDispatcher.class);
        testSubject = new ClusterController(messagingPlatformConfiguration, clusterConfiguration,
                                            clusterNodeRepository,
                                            tagsConfiguration,
                                            stubFactory,
                                            mockRaftGroupRepositoryManager,
                                            queryDispatcher, commandDispatcher,
                                            eventPublisher, limits, channelCloser);
    }

    @Test
    public void startAndStop()  {
        assertTrue(testSubject.isAutoStartup());
        assertFalse(testSubject.isRunning());
        testSubject.start();
        assertTrue(testSubject.isRunning());
        AtomicBoolean stopped = new AtomicBoolean(false);
        testSubject.stop(() -> stopped.set(true));
        assertFalse(testSubject.isRunning());
        assertTrue(stopped.get());
    }

    @Test
    public void getNodes() throws InterruptedException {
        entityManager.persist(new ClusterNode("name", "hostName", "localhost", 0, 1000, 0));
        testSubject.start();
        Thread.sleep(250);
        Collection<RemoteConnection> nodes = testSubject.getRemoteConnections();

        assertEquals(1, nodes.size());
        Iterator<RemoteConnection> remoteConnectionIterator = nodes.iterator();
        RemoteConnection remoteConnection = remoteConnectionIterator.next();
        assertWithin(1, TimeUnit.SECONDS, () -> assertTrue(remoteConnection.isConnected()));
    }

    @Test
    public void addConnection()  {
        AtomicBoolean listenerCalled = new AtomicBoolean(false);
        testSubject.addNodeListener(event -> listenerCalled.set(true));
        testSubject.addConnection(NodeInfo.newBuilder()
                .setNodeName("newName")
                .setInternalHostName("newHostName")
                .setGrpcInternalPort(0)
                .build());

        Collection<RemoteConnection> nodes = testSubject.getRemoteConnections();
        assertEquals(1, nodes.size());
        assertFalse(nodes.iterator().next().isConnected());
        assertTrue(listenerCalled.get());
    }

    @Test
    public void getMe()  {
        ClusterNode me = testSubject.getMe();
        assertEquals("MyName", me.getName());
        assertEquals("LAPTOP-1QH9GIHL.axoniq.io", me.getHostName());
        assertEquals("LAPTOP-1QH9GIHL.axoniq.net", me.getInternalHostName());
        assertEquals(new HashMap<String,String>(){{put("some","tag");}}, me.getTags());
    }

    @Test
    public void messagingNodes() {
        long initial = testSubject.nodes().count();
        testSubject.addConnection(NodeInfo.newBuilder()
                .setNodeName("newName")
                .setInternalHostName("newHostName")
                .setGrpcInternalPort(0)
                .build());
        assertEquals(initial+1, testSubject.nodes().count());
        testSubject.addConnection(NodeInfo.newBuilder()
                .setNodeName("newName")
                .setInternalHostName("newHostName")
                .setGrpcInternalPort(0)
                .build());
        assertEquals(initial+1, testSubject.nodes().count());
    }

    @Test
    public void sendDeleteNode() {
        AtomicBoolean listenerCalled = new AtomicBoolean(false);
        testSubject.addNodeListener(event -> {
            if(ClusterEvent.EventType.NODE_DELETED.equals(event.getEventType())) listenerCalled.set(true);
        });
        testSubject.addConnection(NodeInfo.newBuilder()
                .setNodeName("newName")
                .setInternalHostName("newHostName")
                .setGrpcInternalPort(0)
                .build());
        testSubject.addConnection(NodeInfo.newBuilder()
                .setNodeName("deletedNode")
                .setInternalHostName("newHostName")
                .setGrpcInternalPort(0)
                .build());


        testSubject.sendDeleteNode("deletedNode");
        assertTrue(listenerCalled.get());
    }

}