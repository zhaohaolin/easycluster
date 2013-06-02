package org.easycluster.easycluster.cluster.netty.http;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import junit.framework.Assert;

import org.easycluster.easycluster.cluster.SampleMessageClosure;
import org.easycluster.easycluster.cluster.SampleRequest;
import org.easycluster.easycluster.cluster.SampleResponse;
import org.easycluster.easycluster.cluster.client.loadbalancer.RoundRobinLoadBalancerFactory;
import org.easycluster.easycluster.serialization.protocol.meta.MetainfoUtils;
import org.easycluster.easycluster.serialization.protocol.meta.MsgCode2TypeMetainfo;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class HttpNetworkTestCase {

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testSend_binary() {
		List<String> packages = new ArrayList<String>();
		packages.add("org.easycluster.easycluster.cluster");
		MsgCode2TypeMetainfo typeMetaInfo = MetainfoUtils.createTypeMetainfo(packages);

		HttpNetworkServer server = new HttpNetworkServer("app", "test", "127.0.0.1:2181");
		server.registerHandler(SampleRequest.class, SampleResponse.class, new SampleMessageClosure());
		server.setPort(5000);
		server.setPartitionIds(new Integer[] { 0, 1 });
		HttpRequestDecoder httpRequestDecoder = new HttpRequestDecoder();
		httpRequestDecoder.setTypeMetaInfo(typeMetaInfo);
		httpRequestDecoder.setDebugEnabled(true);
		server.setDecoder(httpRequestDecoder);
		HttpResponseEncoder responseEncoder = new HttpResponseEncoder();
		responseEncoder.setDebugEnabled(false);
		server.setEncoder(responseEncoder);
		server.start();

		HttpNetworkClient client = new HttpNetworkClient("app", "test", "127.0.0.1:2181", new RoundRobinLoadBalancerFactory());
		HttpResponseDecoder responseDecoder = new HttpResponseDecoder();
		responseDecoder.setTypeMetaInfo(typeMetaInfo);
		responseDecoder.setDebugEnabled(false);
		client.setDecoder(responseDecoder);
		HttpRequestEncoder requestEncoder = new HttpRequestEncoder();
		requestEncoder.setDebugEnabled(false);
		client.setEncoder(requestEncoder);
		client.registerRequest(SampleRequest.class, SampleResponse.class);
		client.start();

		int num = 50000;

		List<SampleRequest> client1Requests = new ArrayList<SampleRequest>();

		for (int i = 0; i < num; i++) {
			SampleRequest request = new SampleRequest();
			request.setIntField(1);
			request.setShortField((byte) 1);
			request.setByteField((byte) 1);
			request.setLongField(1L);
			request.setStringField("test");

			request.setByteArrayField(new byte[] { 127 });

			client1Requests.add(request);
		}

		long startTime = System.nanoTime();

		final List<Future<Object>> futures = new ArrayList<Future<Object>>(num);

		for (int i = 0; i < num; i++) {
			futures.add(client.sendMessage(client1Requests.get(i)));
		}

		final List<SampleResponse> client1Responses = new ArrayList<SampleResponse>();
		for (int i = 0; i < num; i++) {
			try {
				Object object = futures.get(i).get(1800, TimeUnit.SECONDS);
				if (object instanceof SampleResponse) {
					client1Responses.add((SampleResponse) object);
				} else {
					System.out.println(object);
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (ExecutionException e) {
				e.printStackTrace();
			} catch (TimeoutException e) {
				e.printStackTrace();
			}
		}
		Assert.assertEquals(num, client1Responses.size());

		long endTime = System.nanoTime();
		System.out.println("Runtime estimated: " + (endTime - startTime) / 1000000 + "ms.");

		client.stop();
		server.stop();
	}

	@Test
	public void testSend_json() throws Exception {
		List<String> packages = new ArrayList<String>();
		packages.add("org.easycluster.easycluster.cluster");
		MsgCode2TypeMetainfo typeMetaInfo = MetainfoUtils.createTypeMetainfo(packages);

		HttpNetworkServer server = new HttpNetworkServer("app", "test", "127.0.0.1:2181");
		server.registerHandler(SampleRequest.class, SampleResponse.class, new SampleMessageClosure());
		server.setPort(5000);
		server.setPartitionIds(new Integer[] { 0, 1 });
		HttpRequestDecoder httpRequestDecoder = new HttpRequestDecoder();
		httpRequestDecoder.setTypeMetaInfo(typeMetaInfo);
		httpRequestDecoder.setDebugEnabled(true);
		server.setDecoder(httpRequestDecoder);
		HttpResponseEncoder responseEncoder = new HttpResponseEncoder();
		responseEncoder.setDebugEnabled(false);
		server.setEncoder(responseEncoder);
		server.start();

		HttpNetworkClient client = new HttpNetworkClient("app", "test", "127.0.0.1:2181", new RoundRobinLoadBalancerFactory());
		HttpResponseDecoder responseDecoder = new HttpResponseDecoder();
		responseDecoder.setTypeMetaInfo(typeMetaInfo);
		responseDecoder.setDebugEnabled(false);
		client.setDecoder(responseDecoder);
		HttpRequestEncoder requestEncoder = new HttpRequestEncoder();
		requestEncoder.setDebugEnabled(false);
		client.setEncoder(requestEncoder);
		client.registerRequest(SampleRequest.class, SampleResponse.class);
		client.start();

		int num = 50000;

		List<SampleRequest> client1Requests = new ArrayList<SampleRequest>();

		for (int i = 0; i < num; i++) {
			SampleRequest request = new SampleRequest();
			request.setIntField(1);
			request.setShortField((byte) 1);
			request.setByteField((byte) 1);
			request.setLongField(1L);
			request.setStringField("test");

			request.setByteArrayField(new byte[] { 127 });

			client1Requests.add(request);
		}

		long startTime = System.nanoTime();

		final List<Future<Object>> futures = new ArrayList<Future<Object>>(num);

		for (int i = 0; i < num; i++) {
			futures.add(client.sendMessage(client1Requests.get(i)));
		}

		final List<SampleResponse> client1Responses = new ArrayList<SampleResponse>();
		for (int i = 0; i < num; i++) {
			try {
				Object object = futures.get(i).get(1800, TimeUnit.SECONDS);
				if (object instanceof SampleResponse) {
					client1Responses.add((SampleResponse) object);
				} else {
					System.out.println(object);
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (ExecutionException e) {
				e.printStackTrace();
			} catch (TimeoutException e) {
				e.printStackTrace();
			}
		}
		Assert.assertEquals(num, client1Responses.size());

		long endTime = System.nanoTime();
		System.out.println("Runtime estimated: " + (endTime - startTime) / 1000000 + "ms.");

		client.stop();
		server.stop();
	}

}
