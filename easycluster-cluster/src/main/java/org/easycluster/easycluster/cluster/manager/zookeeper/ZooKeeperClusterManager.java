package org.easycluster.easycluster.cluster.manager.zookeeper;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.KeeperException.NodeExistsException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.Watcher.Event.EventType;
import org.apache.zookeeper.Watcher.Event.KeeperState;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;
import org.easycluster.easycluster.cluster.Node;
import org.easycluster.easycluster.cluster.manager.ClusterManager;
import org.easycluster.easycluster.cluster.manager.ClusterNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSON;

public class ZooKeeperClusterManager implements ClusterManager {

	private static final Logger	LOGGER				= LoggerFactory.getLogger(ZooKeeperClusterManager.class);

	private static final String	NODE_SEPARATOR		= "/";

	private ClusterNotification	clusterNotification	= null;
	private String				connectString		= "";
	private int					sessionTimeout		= 0;
	private String				rootNode			= "/clusters";
	private String				serviceGroupNode	= null;
	private String				serviceNode			= null;
	private String				membershipNode		= null;
	private String				eventNode			= null;
	private String				availabilityNode	= null;

	private Map<String, Node>	currentNodes		= new HashMap<String, Node>();
	private ZooKeeper			zooKeeper			= null;
	private ClusterWatcher		watcher				= null;
	private volatile boolean	connected			= false;

	public ZooKeeperClusterManager(String serviceGroup, String service, String zooKeeperConnectString, int zooKeeperSessionTimeoutMillis) {
		this.connectString = zooKeeperConnectString;
		this.sessionTimeout = zooKeeperSessionTimeoutMillis;
		this.serviceGroupNode = rootNode + NODE_SEPARATOR + serviceGroup;
		this.serviceNode = serviceGroupNode + NODE_SEPARATOR + service;
		this.membershipNode = serviceNode + NODE_SEPARATOR + "members";
		this.eventNode = serviceNode + NODE_SEPARATOR + "event";
		this.availabilityNode = serviceNode + NODE_SEPARATOR + "available";

		this.clusterNotification = new ClusterNotification(service);
	}

	@Override
	public void start() {
		startZooKeeper();
	}

	@Override
	public void addNode(final Node node) {
		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("addNode {}", node);
		}

		String event = "AddNode";
		if (!connected) {
			LOGGER.error("{} when not connected", event);
			return;
		}

		doWithZooKeeper(event, zooKeeper, new ZooKeeperStatement() {

			public void doInZooKeeper(ZooKeeper zk) throws KeeperException, InterruptedException {
				String path = membershipNode + NODE_SEPARATOR + node.getId();

				String nodeString = JSON.toJSONString(node);

				try {
					if (LOGGER.isDebugEnabled()) {
						LOGGER.debug("creating node - path=[{}], data=[{}]", path, nodeString);
					}
					zk.create(path, nodeString.getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
				} catch (KeeperException.NodeExistsException ex) {
					if (LOGGER.isDebugEnabled()) {
						LOGGER.debug("A node with id " + node.getId() + " already exists");
					}
					Stat stat = new Stat();
					zk.getData(path, false, stat);
					if (LOGGER.isDebugEnabled()) {
						LOGGER.debug("setData - path=[{}], data=[{}], version=[{}]", new Object[] { path, nodeString, stat.getVersion() });
					}
					zk.setData(path, nodeString.getBytes(), stat.getVersion());
				}

				currentNodes.put(node.getId(), node);
				clusterNotification.handleNodesChanged(currentNodes.values());
			}
		});

	}

	@Override
	public void removeNode(final String nodeId) {
		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("RemoveNode {}", nodeId);
		}

		String event = "RemoveNode";
		if (!connected) {
			LOGGER.error("{} when not connected", event);
			return;
		}

		doWithZooKeeper(event, zooKeeper, new ZooKeeperStatement() {

			public void doInZooKeeper(ZooKeeper zk) throws KeeperException, InterruptedException {
				String path = membershipNode + NODE_SEPARATOR + nodeId;

				if (zk.exists(path, false) != null) {
					try {
						zk.delete(path, -1);
					} catch (KeeperException.NoNodeException ex) {
						// do nothing
					}
				}

				currentNodes.remove(nodeId);
				clusterNotification.handleNodesChanged(currentNodes.values());
			}
		});
	}

	@Override
	public void markNodeAvailable(final String nodeId) {
		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("MarkNodeAvailable {}", nodeId);
		}

		String event = "MarkNodeAvailable";
		if (!connected) {
			LOGGER.error("{} when not connected", event);
			return;
		}

		doWithZooKeeper(event, zooKeeper, new ZooKeeperStatement() {

			public void doInZooKeeper(ZooKeeper zk) throws KeeperException, InterruptedException {
				String path = availabilityNode + NODE_SEPARATOR + nodeId + "-";

				if (LOGGER.isDebugEnabled()) {
					LOGGER.debug("creating node {}", path);
				}
				String node = zk.create(path, null, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);
				if (LOGGER.isDebugEnabled()) {
					LOGGER.debug("Path [{}] created.", node);
				}

				makeNodeAvailable(nodeId);
				clusterNotification.handleNodesChanged(currentNodes.values());
			}
		});

	}

	@Override
	public void markNodeUnavailable(final String nodeId) {
		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("MarkNodeUnavailable {}", nodeId);
		}

		String event = "MarkNodeUnavailable";
		if (!connected) {
			LOGGER.error("{} when not connected", event);
			return;
		}

		doWithZooKeeper(event, zooKeeper, new ZooKeeperStatement() {

			public void doInZooKeeper(ZooKeeper zk) throws KeeperException, InterruptedException {

				List<String> availableSet = zk.getChildren(availabilityNode, false);

				for (String availableNode : availableSet) {
					if (availableNode.startsWith(nodeId)) {
						String path = availabilityNode + NODE_SEPARATOR + availableNode;
						if (zk.exists(path, false) != null) {
							try {
								zk.delete(path, -1);
							} catch (KeeperException.NoNodeException ex) {
								// do nothing
							}
						}
					}
				}

				makeNodeUnavailable(nodeId);
				clusterNotification.handleNodesChanged(currentNodes.values());
			}
		});
	}

	private void handleMembershipChanged() {
		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("handleMembershipChanged");
		}

		String event = "Membership changed";
		if (!connected) {
			LOGGER.error("{} when not connected", event);
			return;
		}

		doWithZooKeeper(event, zooKeeper, new ZooKeeperStatement() {

			public void doInZooKeeper(ZooKeeper zk) throws KeeperException, InterruptedException {
				lookupCurrentNodes(zk);
				clusterNotification.handleNodesChanged(currentNodes.values());
			}
		});

	}

	private void handleAvailabilityChanged() {
		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("handleAvailabilityChanged");
		}

		String event = "Availability changed";
		if (!connected) {
			LOGGER.error("{} when not connected", event);
			return;
		}

		doWithZooKeeper(event, zooKeeper, new ZooKeeperStatement() {

			public void doInZooKeeper(ZooKeeper zk) throws KeeperException, InterruptedException {
				List<String> availableSet = zk.getChildren(availabilityNode, true);

				if (availableSet.isEmpty()) {
					for (String nodeId : currentNodes.keySet()) {
						makeNodeUnavailable(nodeId);
					}
				} else {
					for (String nodeId : currentNodes.keySet()) {
						boolean available = false;
						for (String availableNode : availableSet) {
							if (availableNode.startsWith(nodeId)) {
								available = true;
								break;
							}
						}

						if (available) {
							makeNodeAvailable(nodeId);
						} else {
							makeNodeUnavailable(nodeId);
						}
					}
				}
				clusterNotification.handleNodesChanged(currentNodes.values());
			}
		});
	}

	private void handleClusterEvent() {
		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("handleClusterEvent");
		}

		String event = "Received cluster event";
		if (!connected) {
			LOGGER.error("{} when not connected", event);
			return;
		}

		doWithZooKeeper(event, zooKeeper, new ZooKeeperStatement() {

			public void doInZooKeeper(ZooKeeper zk) throws KeeperException, InterruptedException {
				byte[] data = zk.getData(eventNode, false, null);
				if (data != null && data.length > 0) {
					clusterNotification.handleClusterEvent(new String(data));
				}
			}
		});

	}

	private void lookupCurrentNodes(ZooKeeper zk) throws KeeperException, InterruptedException {

		List<String> members = zk.getChildren(membershipNode, true);
		List<String> availableSet = zk.getChildren(availabilityNode, true);

		currentNodes.clear();

		for (String nodeId : members) {
			byte[] data = zk.getData(membershipNode + NODE_SEPARATOR + nodeId, false, null);
			Node node = JSON.parseObject(new String(data), Node.class);
			if (node != null) {
				boolean available = false;
				for (String availableNode : availableSet) {
					if (availableNode.startsWith(node.getId())) {
						available = true;
						break;
					}
				}

				node.setAvailable(available);
				currentNodes.put(nodeId, node);
			}
		}
	}

	private void makeNodeAvailable(String nodeId) {
		Node old = currentNodes.get(nodeId);
		if (!old.getAvailable()) {
			old.setAvailable(true);
			currentNodes.put(old.getId(), old);
		}
	}

	private void makeNodeUnavailable(String nodeId) {
		Node old = currentNodes.get(nodeId);
		if (old.getAvailable()) {
			old.setAvailable(false);
			currentNodes.put(old.getId(), old);
		}
	}

	@Override
	public void shutdown() {
		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("Handling a Shutdown message");
		}

		try {
			watcher.shutdown();
			zooKeeper.close();
		} catch (Exception ex) {
			LOGGER.error("Exception when closing connection to ZooKeeper", ex);
		}

		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("ZooKeeperClusterManager shut down");
		}
	}

	private void handleConnected() {
		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("handleConnected");
		}

		String event = "Connected";
		if (connected) {
			LOGGER.error("{} when already connected", event);
			return;
		}

		doWithZooKeeper(event, zooKeeper, new ZooKeeperStatement() {
			public void doInZooKeeper(ZooKeeper zk) throws KeeperException, InterruptedException {
				verifyZooKeeperStructure(zk);
				lookupCurrentNodes(zk);
				connected = true;
				clusterNotification.handleConnected(currentNodes.values());
			}
		});
	}

	private void handleDisconnected() {
		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("handleDisconnected");
		}

		if (!connected) {
			LOGGER.error("Disconnected when not connected");
		} else {
			connected = false;
			currentNodes.clear();
			clusterNotification.handleDisconnected();
		}

	}

	private void handleExpired() {
		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("handleExpired");
		}

		LOGGER.error("Connection to ZooKeeper expired, reconnecting...");

		connected = false;
		currentNodes.clear();
		watcher.shutdown();
		startZooKeeper();
	}

	private void startZooKeeper() {
		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("Connecting to ZooKeeper...");
		}

		try {
			watcher = new ClusterWatcher(this);
			zooKeeper = new ZooKeeper(connectString, sessionTimeout, watcher);

			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug("Connected to ZooKeeper");
			}
		} catch (IOException ex) {
			LOGGER.error("Unable to connect to ZooKeeper", ex);
		} catch (Exception e) {
			LOGGER.error("Exception while connecting to ZooKeeper", e);
		}
	}

	private void verifyZooKeeperStructure(ZooKeeper zk) throws KeeperException, InterruptedException {
		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("Verifying ZooKeeper structure...");
		}

		for (String path : new String[] { rootNode, serviceGroupNode, serviceNode, membershipNode, availabilityNode, eventNode }) {
			try {
				if (LOGGER.isDebugEnabled()) {
					LOGGER.debug("Ensuring {} exists", path);
				}
				if (zk.exists(path, false) == null) {
					if (LOGGER.isDebugEnabled()) {
						LOGGER.debug("{} doesn't exist, creating", path);
					}
					zk.create(path, null, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
				}
			} catch (NodeExistsException ex) {
				// do nothing
			}
		}
	}

	private void doWithZooKeeper(String event, ZooKeeper zk, ZooKeeperStatement action) {
		if (zooKeeper == null) {
			LOGGER.error("{} when ZooKeeper is null, this should never happen. ", event);
			return;
		}

		try {
			action.doInZooKeeper(zk);
		} catch (KeeperException ex) {
			LOGGER.error("ZooKeeper threw an exception", ex);
		} catch (Exception ex) {
			LOGGER.error("Unhandled exception while working with ZooKeeper", ex);
		}

	}

	interface ZooKeeperStatement {
		void doInZooKeeper(ZooKeeper zk) throws KeeperException, InterruptedException;
	}

	public void setClusterNotification(ClusterNotification clusterNotification) {
		this.clusterNotification = clusterNotification;
	}

	class ClusterWatcher implements Watcher {
		private volatile boolean		shutdownSwitch	= false;

		private ZooKeeperClusterManager	zooKeeperManager;

		public ClusterWatcher(ZooKeeperClusterManager zooKeeperManager) {
			this.zooKeeperManager = zooKeeperManager;
		}

		public void process(WatchedEvent event) {
			if (shutdownSwitch) {
				return;
			}

			if (event.getType() == EventType.None) {
				if (event.getState() == KeeperState.SyncConnected) {
					zooKeeperManager.handleConnected();
				} else if (event.getState() == KeeperState.Expired) {
					zooKeeperManager.handleExpired();
				} else if (event.getState() == KeeperState.Disconnected) {
					zooKeeperManager.handleDisconnected();
				}
			} else if (event.getType() == EventType.NodeChildrenChanged) {
				if (event.getPath().equals(membershipNode)) {
					zooKeeperManager.handleMembershipChanged();
				} else if (event.getPath().equals(availabilityNode)) {
					zooKeeperManager.handleAvailabilityChanged();
				} else {
					LOGGER.error("Received a notification for a path that shouldn't be monitored: {}", event.getPath());
				}
			} else if (event.getType() == EventType.NodeDataChanged) {
				if (event.getPath().equals(eventNode)) {
					zooKeeperManager.handleClusterEvent();
				}
			}

		}

		public void shutdown() {
			shutdownSwitch = true;
		}
	}

}
