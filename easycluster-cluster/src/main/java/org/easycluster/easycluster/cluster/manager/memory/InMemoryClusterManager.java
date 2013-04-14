package org.easycluster.easycluster.cluster.manager.memory;

import java.util.HashMap;
import java.util.Map;

import org.easycluster.easycluster.cluster.Node;
import org.easycluster.easycluster.cluster.exception.InvalidNodeException;
import org.easycluster.easycluster.cluster.manager.ClusterManager;
import org.easycluster.easycluster.cluster.manager.ClusterNotification;


public class InMemoryClusterManager implements ClusterManager {

	private ClusterNotification	clusterNotification	= null;
	private Map<Integer, Node>	currentNodes				= new HashMap<Integer, Node>();

	public InMemoryClusterManager(String serviceName) {
		this.clusterNotification = new ClusterNotification(serviceName);
	}

	@Override
	public void start() {
	}

	@Override
	public void shutdown() {
	}

	@Override
	public void addNode(Node node) {
		if (currentNodes.containsKey(node.getId())) {
			throw new InvalidNodeException("A node with id " + node.getId() + " already exists");
		} else {
			currentNodes.put(node.getId(), node);
			clusterNotification.handleNodesChanged(currentNodes.values());
		}
	}

	@Override
	public void removeNode(int nodeId) {
		currentNodes.remove(nodeId);
		clusterNotification.handleNodesChanged(currentNodes.values());
	}

	@Override
	public void markNodeAvailable(int nodeId) {
		Node old = currentNodes.get(nodeId);
		currentNodes.put(nodeId, new Node(old.getId(), old.getHostName(), old.getPort(), old.getPartitions(), true));
		clusterNotification.handleNodesChanged(currentNodes.values());
	}

	@Override
	public void markNodeUnavailable(int nodeId) {
		Node old = currentNodes.get(nodeId);
		currentNodes.put(nodeId, new Node(old.getId(), old.getHostName(), old.getPort(), old.getPartitions(), false));
		clusterNotification.handleNodesChanged(currentNodes.values());
	}

	public void setClusterNotification(ClusterNotification clusterNotification) {
		this.clusterNotification = clusterNotification;
	}

}
