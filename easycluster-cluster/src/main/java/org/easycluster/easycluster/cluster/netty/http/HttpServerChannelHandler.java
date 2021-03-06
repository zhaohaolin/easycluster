package org.easycluster.easycluster.cluster.netty.http;

import org.easycluster.easycluster.cluster.exception.InvalidMessageException;
import org.easycluster.easycluster.cluster.netty.endpoint.DefaultEndpointFactory;
import org.easycluster.easycluster.cluster.netty.endpoint.Endpoint;
import org.easycluster.easycluster.cluster.netty.endpoint.EndpointFactory;
import org.easycluster.easycluster.cluster.netty.endpoint.EndpointListener;
import org.easycluster.easycluster.cluster.server.MessageClosureRegistry;
import org.easycluster.easycluster.cluster.server.MessageExecutor;
import org.easycluster.easycluster.core.Closure;
import org.easycluster.easycluster.core.KeyTransformer;
import org.easycluster.easycluster.core.Transformer;
import org.easycluster.easycluster.core.TransportUtil;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelLocal;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.timeout.IdleStateAwareChannelUpstreamHandler;
import org.jboss.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpServerChannelHandler extends IdleStateAwareChannelUpstreamHandler {

	private static final Logger					LOGGER					= LoggerFactory.getLogger(HttpServerChannelHandler.class);

	private ChannelGroup						channelGroup			= null;
	private MessageClosureRegistry				messageHandlerRegistry	= null;
	private MessageExecutor						messageExecutor			= null;
	private KeyTransformer						keyTransformer			= new HttpKeyTransformer();
	private EndpointFactory						endpointFactory			= new DefaultEndpointFactory();
	private final ChannelLocal<Endpoint>		endpoints				= new ChannelLocal<Endpoint>();
	private Transformer<HttpRequest, Object>	requestTransformer		= null;
	private Transformer<Object, HttpResponse>	responseTransformer		= null;

	public HttpServerChannelHandler(ChannelGroup channelGroup, MessageClosureRegistry messageHandlerRegistry, MessageExecutor messageExecutor) {
		this.channelGroup = channelGroup;
		this.messageHandlerRegistry = messageHandlerRegistry;
		this.messageExecutor = messageExecutor;
	}

	@Override
	public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e) {
		Channel channel = e.getChannel();
		if (LOGGER.isTraceEnabled()) {
			LOGGER.trace("channelOpen: " + channel);
		}
		Endpoint endpoint = endpointFactory.createEndpoint(e.getChannel());
		if (null != endpoint) {
			attachEndpointToSession(e.getChannel(), endpoint);
		}
		channelGroup.add(channel);

	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
		LOGGER.error("channel: [" + e.getChannel().getRemoteAddress() + "], exceptionCaught:", e.getCause());
		// ctx.getChannel().close();
	}

	@Override
	public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("channelClosed: [" + e.getChannel().getRemoteAddress() + "]");
		}
		Endpoint endpoint = removeEndpointOfSession(e.getChannel());
		if (null != endpoint) {
			endpoint.stop();
		}
	}

	@Override
	public void channelIdle(ChannelHandlerContext ctx, IdleStateEvent e) throws Exception {
		if (LOGGER.isInfoEnabled()) {
			LOGGER.info("channelIdle: " + e.getState().name() + " for " + (System.currentTimeMillis() - e.getLastActivityTimeMillis())
					+ " milliseconds, close channel[" + e.getChannel().getRemoteAddress() + "]");
		}
		e.getChannel().close();
	}

	@Override
	public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) {
		if (LOGGER.isTraceEnabled()) {
			LOGGER.trace("message received {}", e.getMessage());
		}

		Channel channel = e.getChannel();
		Object request = e.getMessage();

		Endpoint endpoint = getEndpointOfSession(channel);

		if (endpoint == null) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e1) {
				LOGGER.error("", e1);
			}
			endpoint = getEndpointOfSession(channel);
		}

		if (null != endpoint) {

			HttpResponseHandler responseHandler = new HttpResponseHandler(channel, (HttpRequest) request);

			Object signal = requestTransformer.transform((HttpRequest) request);

			TransportUtil.attachSender(signal, endpoint);

			if (!messageHandlerRegistry.messageRegistered(signal.getClass())) {
				String error = String.format("No such message of type %s registered", signal.getClass().getName());
				LOGGER.warn(error);
				responseHandler.execute(new InvalidMessageException(error));
			} else {
				messageExecutor.execute(signal, responseHandler);
			}

		} else {
			LOGGER.warn("missing endpoint, ignore incoming msg:", request);
			// error reactor
		}

	}

	public void attachEndpointToSession(Channel channel, Endpoint endpoint) {
		endpoints.set(channel, endpoint);
	}

	public Endpoint getEndpointOfSession(Channel channel) {
		return (Endpoint) endpoints.get(channel);
	}

	public Endpoint removeEndpointOfSession(Channel channel) {
		return (Endpoint) endpoints.remove(channel);
	}

	class HttpResponseHandler implements Closure {
		private Channel	channel;
		private HttpRequest	request;
		private Object		requestId;

		public HttpResponseHandler(Channel channel, HttpRequest request) {
			this.channel = channel;
			this.request = request;
			this.requestId = keyTransformer.transform(request);
		}

		@Override
		public void execute(Object message) {
			if (message instanceof Exception) {
				Exception ex = (Exception) message;
				message = buildErrorResponse(ex);
			}

			doSend(message);
		}

		private Object buildErrorResponse(Exception ex) {
			Class<?> responseType = messageHandlerRegistry.getResponseFor(request);
			if (responseType == null) {
				return null;
			}

			Object response = null;
			try {
				response = responseType.newInstance();
				// TODO set exception message
			} catch (Exception e) {
				LOGGER.warn("Build default response with error " + e.getMessage(), e);
			}
			return response;
		}

		private void doSend(Object message) {
			if (message != null) {
				HttpResponse response = responseTransformer.transform(message);

				response.setHeader("uuid", requestId);

				// 是否需要持久连接
				String keepAlive = request.getHeader(HttpHeaders.Names.CONNECTION);
				if (keepAlive != null) {
					response.setHeader(HttpHeaders.Names.CONNECTION, keepAlive);
				}

				channel.write(response);
			}
		}
	}

	public void setEndpointFactory(EndpointFactory endpointFactory) {
		this.endpointFactory = endpointFactory;
	}

	public void setEndpointListener(EndpointListener endpointListener) {
		this.endpointFactory.setEndpointListener(endpointListener);
	}

	public void setKeyTransformer(KeyTransformer keyTransformer) {
		this.keyTransformer = keyTransformer;
	}

	public void setRequestTransformer(Transformer<HttpRequest, Object> requestTransformer) {
		this.requestTransformer = requestTransformer;
	}

	public void setResponseTransformer(Transformer<Object, HttpResponse> responseTransformer) {
		this.responseTransformer = responseTransformer;
	}

}
