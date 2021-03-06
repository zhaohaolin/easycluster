//package org.easycluster.easycluster.cluster.netty.websocket;
//
//import org.easycluster.easycluster.cluster.netty.codec.ByteBeanDecoder;
//import org.easycluster.easycluster.serialization.protocol.meta.Int2TypeMetainfo;
//import org.jboss.netty.buffer.ChannelBuffer;
//import org.jboss.netty.channel.Channel;
//import org.jboss.netty.channel.ChannelHandlerContext;
//import org.jboss.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
//import org.jboss.netty.handler.codec.oneone.OneToOneDecoder;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//public class BinaryWebSocketFrameDecoder extends OneToOneDecoder {
//
//	private static final Logger	LOGGER			= LoggerFactory.getLogger(BinaryWebSocketFrameDecoder.class);
//
//	private ByteBeanDecoder		byteBeanDecoder	= new ByteBeanDecoder();
//
//	@Override
//	protected Object decode(ChannelHandlerContext ctx, Channel channel, Object msg) throws Exception {
//		if (msg instanceof BinaryWebSocketFrame) {
//			if (LOGGER.isDebugEnabled()) {
//				LOGGER.debug("receive websocket frame: [{}]", msg);
//			}
//
//			ChannelBuffer content = ((BinaryWebSocketFrame) msg).getBinaryData();
//			if (null != content) {
//				return byteBeanDecoder.transform(content.array());
//			}
//		}
//		return msg;
//	}
//
//	public void setByteBeanDecoder(ByteBeanDecoder byteBeanDecoder) {
//		this.byteBeanDecoder = byteBeanDecoder;
//	}
//
//	public void setTypeMetaInfo(Int2TypeMetainfo typeMetaInfo) {
//		byteBeanDecoder.setTypeMetaInfo(typeMetaInfo);
//	}
//
//	public void setDumpBytes(int dumpBytes) {
//		byteBeanDecoder.setDumpBytes(dumpBytes);
//	}
//
//	public void setDebugEnabled(boolean isDebugEnabled) {
//		byteBeanDecoder.setDebugEnabled(isDebugEnabled);
//	}
//
//	public void setEncryptKey(String encryptKey) {
//		byteBeanDecoder.setEncryptKey(encryptKey);
//	}
//}
