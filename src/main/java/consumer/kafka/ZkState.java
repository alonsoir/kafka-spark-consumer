package consumer.kafka;

import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

import org.apache.zookeeper.CreateMode;
import org.json.simple.JSONValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.curator.framework.CuratorFramework;
import com.netflix.curator.framework.CuratorFrameworkFactory;
import com.netflix.curator.retry.RetryNTimes;

public class ZkState implements Serializable{
	public static final Logger LOG = LoggerFactory.getLogger(ZkState.class);
	transient CuratorFramework _curator;

	private CuratorFramework newCurator(Map stateConf) throws Exception {
		Integer port = Integer.parseInt((String) stateConf
				.get(Config.ZOOKEEPER_PORT));
		String serverPorts = "";
		for (String server : (List<String>) stateConf
				.get(Config.ZOOKEEPER_HOSTS)) {
			serverPorts = serverPorts + server + ":" + port + ",";
		}
		return CuratorFrameworkFactory.newClient(serverPorts, 120000, 120000,
				new RetryNTimes(5, 1000));
	}

	public CuratorFramework getCurator() {
		assert _curator != null;
		return _curator;
	}

	public ZkState(KafkaConfig config) {

		try {
			_curator = newCurator(config._stateConf);
			LOG.info("Starting curator service");
			_curator.start();
		} catch (Exception e) {
			LOG.error("Curator service not started");
			throw new RuntimeException(e);
		}
	}
	
	public ZkState(String  connectionStr) {

		try {
			_curator = CuratorFrameworkFactory.newClient(connectionStr, 120000, 120000,
					new RetryNTimes(5, 1000));
			LOG.info("Starting curator service");
			_curator.start();
		} catch (Exception e) {
			LOG.error("Curator service not started");
			throw new RuntimeException(e);
		}
	}

	public void writeJSON(String path, Map<Object, Object> data) {
		LOG.info("Writing " + path + " the data " + data.toString());
		writeBytes(path,
				JSONValue.toJSONString(data).getBytes(Charset.forName("UTF-8")));
	}

	public void writeBytes(String path, byte[] bytes) {
		try {
			if (_curator.checkExists().forPath(path) == null) {
				_curator.create().creatingParentsIfNeeded()
						.withMode(CreateMode.PERSISTENT).forPath(path, bytes);
			} else {
				_curator.setData().forPath(path, bytes);
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public Map<Object, Object> readJSON(String path) {
		try {
			byte[] b = readBytes(path);
			if (b == null) {
				return null;
			}
			return (Map<Object, Object>) JSONValue
					.parse(new String(b, "UTF-8"));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public byte[] readBytes(String path) {
		try {
			if (_curator.checkExists().forPath(path) != null) {
				return _curator.getData().forPath(path);
			} else {
				return null;
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public void close() {
		_curator.close();
		_curator = null;
	}
}