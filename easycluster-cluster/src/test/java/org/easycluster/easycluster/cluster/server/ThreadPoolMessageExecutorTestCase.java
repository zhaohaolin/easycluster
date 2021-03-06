package org.easycluster.easycluster.cluster.server;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;

import junit.framework.Assert;

import org.easycluster.easycluster.cluster.SampleMessageClosure;
import org.easycluster.easycluster.cluster.SampleRequest;
import org.easycluster.easycluster.cluster.SampleResponse;
import org.easycluster.easycluster.core.Closure;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ThreadPoolMessageExecutorTestCase {

	private JMXConnectorServer		cs;
	private JMXConnector			cc;
	private MBeanServerConnection	mbsc;

	@Before
	public void setUp() throws Exception {
		MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
		JMXServiceURL url = new JMXServiceURL("service:jmx:rmi://");
		cs = JMXConnectorServerFactory.newJMXConnectorServer(url, null, mbs);
		cs.start();

		JMXServiceURL addr = cs.getAddress();

		// Now make a connector client using the server's address
		cc = JMXConnectorFactory.connect(addr);
		mbsc = cc.getMBeanServerConnection();

		// HtmlAdaptorServer html = new HtmlAdaptorServer();
		// ObjectName html_name = new ObjectName("Adaptor:name=html,port=8082");
		// mbs.registerMBean(html, html_name);
		//
		// html.start();
	}

	@After
	public void tearDown() throws Exception {
		cc.close();
		cs.stop();
	}

	@Test
	public void test() throws Exception {

		MessageClosureRegistry messageClosureRegistry = new MessageClosureRegistry();
		messageClosureRegistry.registerHandler(SampleRequest.class, SampleResponse.class, new SampleMessageClosure());

		MessageExecutor messageExecutor = new ThreadPoolMessageExecutor("thread-pool-executor", 5, 10, 10, messageClosureRegistry);

		int num = 5000;

		List<SampleRequest> client1Requests = new ArrayList<SampleRequest>();
		final List<SampleResponse> client1Responses = new ArrayList<SampleResponse>();
		for (int i = 0; i < num; i++) {
			final int client = 1;
			SampleRequest request = new SampleRequest();
			request.setIntField(1);
			request.setShortField((byte) 1);
			request.setByteField((byte) 1);
			request.setLongField(1L);
			request.setStringField("test");
			request.setClient(client);
			request.setByteArrayField(new byte[] { 127 });
			client1Requests.add(request);
		}

		List<SampleRequest> client2Requests = new ArrayList<SampleRequest>();
		final List<SampleResponse> client2Responses = new ArrayList<SampleResponse>();
		for (int i = 0; i < num; i++) {
			final int client = 2;
			SampleRequest request = new SampleRequest();
			request.setIntField(1);
			request.setShortField((byte) 1);
			request.setByteField((byte) 1);
			request.setLongField(1L);
			request.setStringField("test");
			request.setClient(client);
			request.setByteArrayField(new byte[] { 127 });
			client2Requests.add(request);
		}

		long startTime = System.nanoTime();
		final CountDownLatch latch = new CountDownLatch(num * 2);

		for (int i = 0; i < num; i++) {

			final SampleRequest req = client1Requests.get(i);
			messageExecutor.execute(req, new Closure() {

				@Override
				public void execute(Object msg) {
					SampleResponse resp = (SampleResponse) msg;
					System.out.println("client" + req.getClient() + " receive resp, sequence: " + resp.getIdentification());
					client1Responses.add(resp);
					latch.countDown();
				}
			});

			final SampleRequest req2 = client2Requests.get(i);
			messageExecutor.execute(req, new Closure() {

				@Override
				public void execute(Object msg) {
					SampleResponse resp = (SampleResponse) msg;
					System.out.println("client" + req2.getClient() + " receive resp, sequence: " + resp.getIdentification());
					client2Responses.add(resp);
					latch.countDown();
				}
			});

		}

		latch.await();

		long endTime = System.nanoTime();
		System.out.println("Runtime estimated: " + (endTime - startTime) / 1000000 + "ms.");

		Assert.assertEquals(num, client1Responses.size());
		Assert.assertEquals(num, client2Responses.size());

		ObjectName objectName = new ObjectName("Application:name=RequestProcessor[thread-pool-executor]");

		System.out.println("AverageProcessingTime:" + mbsc.getAttribute(objectName, "AverageProcessingTime") + " ms");
		System.out.println("AverageWaitTime:" + mbsc.getAttribute(objectName, "AverageWaitTime") + " ms");
		System.out.println("QueueSize:" + mbsc.getAttribute(objectName, "QueueSize"));

		MBeanInfo beanInfo = mbsc.getMBeanInfo(objectName);
		System.out.println(beanInfo);

		Assert.assertEquals(10000, ((Long) mbsc.getAttribute(objectName, "RequestCount")).intValue());

	}

}
