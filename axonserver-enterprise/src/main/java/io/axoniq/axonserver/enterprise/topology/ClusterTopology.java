package io.axoniq.axonserver.enterprise.topology;

import io.axoniq.axonserver.enterprise.cluster.ClusterController;
import io.axoniq.axonserver.enterprise.cluster.GrpcRaftController;
import io.axoniq.axonserver.enterprise.cluster.internal.RemoteConnection;
import io.axoniq.axonserver.enterprise.context.ContextController;
import io.axoniq.axonserver.topology.AxonServerNode;
import io.axoniq.axonserver.topology.Topology;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Author: marc
 */
public class ClusterTopology implements Topology {
    private final ClusterController clusterController;
    private final GrpcRaftController raftController;

    public ClusterTopology(ClusterController clusterController, GrpcRaftController raftController) {
        this.clusterController = clusterController;
        this.raftController = raftController;
    }

    @Override
    public String getName() {
        return clusterController.getName();
    }

    @Override
    public boolean isMultiContext() {
        return true;
    }

    @Override
    public boolean isActive(AxonServerNode node) {
        return clusterController.isActive(node.getName());
    }

    @Override
    public Stream<? extends AxonServerNode> messagingNodes() {
        return clusterController.nodes();
    }

    @Override
    public List<AxonServerNode> getRemoteConnections() {
        return clusterController.getRemoteConnections().stream().map(RemoteConnection::getClusterNode).collect(Collectors.toList());
    }

    @Override
    public AxonServerNode getMe() {
        return clusterController.getMe();
    }

    @Override
    public Iterable<String> getMyMessagingContextsNames() {
        return clusterController.getMyMessagingContexts();
    }

    @Override
    public AxonServerNode findNodeForClient(String clientName, String componentName, String context) {
        return clusterController.findNodeForClient(clientName, componentName, context);
    }

    @Override
    public Iterable<String> getMyStorageContextNames() {
        return raftController.getMyStorageContexts();
    }
}
