
package org.easycluster.easycluster.serialization.bytebean.codec;

import org.easycluster.easycluster.serialization.bytebean.context.DecContext;
import org.easycluster.easycluster.serialization.bytebean.context.DecResult;
import org.easycluster.easycluster.serialization.bytebean.context.EncContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * TODO
 * 
 * @author wangqi
 * @version $Id: AnyCodec.java 14 2012-01-10 11:54:14Z archie $
 */
public class AnyCodec implements ByteFieldCodec {

	private static final Logger logger = LoggerFactory.getLogger(AnyCodec.class);

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.taotaosou.common.serialization.bytebean.field.ByteFieldCodec#getCategory
	 * ()
	 */
	@Override
	public FieldCodecCategory getCategory() {
		return FieldCodecCategory.ANY;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.taotaosou.common.serialization.bytebean.field.ByteFieldCodec#getFieldType
	 * ()
	 */
	@Override
	public Class<?>[] getFieldType() {
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.taotaosou.common.serialization.bytebean.field.ByteFieldCodec#decode
	 * (com.taotaosou.common.serialization.bytebean.context.DecContext)
	 */
	@Override
	public DecResult decode(DecContext ctx) {
		Class<?> clazz = ctx.getDecClass();

		ByteFieldCodec codec = ctx.getCodecOf(clazz);
		if (null == codec) {
			if (clazz.isArray()) {
				codec = ctx.getCodecOf(FieldCodecCategory.ARRAY);
			} else {
				codec = ctx.getCodecOf(FieldCodecCategory.BEAN);
			}
		}

		if (null != codec) {
			return codec.decode(ctx);
		} else {
			logger.error("decode : can not find matched codec for field ["
					+ ctx.getField() + "].");
		}
		return new DecResult(null, ctx.getDecBytes());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.taotaosou.common.serialization.bytebean.field.ByteFieldCodec#encode
	 * (com.taotaosou.common.serialization.bytebean.context.EncContext)
	 */
	@Override
	public byte[] encode(EncContext ctx) {
		Class<?> clazz = ctx.getEncClass();

		ByteFieldCodec codec = ctx.getCodecOf(clazz);
		if (null == codec) {
			if (clazz.isArray()) {
				codec = ctx.getCodecOf(FieldCodecCategory.ARRAY);
			} else {
				codec = ctx.getCodecOf(FieldCodecCategory.BEAN);
			}
		}

		if (null != codec) {
			return codec.encode(ctx);
		} else {
			logger.error("encode : can not find matched codec for field ["
					+ ctx.getField() + "].");
		}

		return new byte[0];
	}

}
