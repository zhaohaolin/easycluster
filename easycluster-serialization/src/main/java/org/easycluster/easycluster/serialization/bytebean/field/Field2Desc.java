
package org.easycluster.easycluster.serialization.bytebean.field;

import java.lang.reflect.Field;

/**
 * 读取给定字段的编解码描述
 * 
 * @author Archibald.Wang
 * @version $Id: Field2Desc.java 14 2012-01-10 11:54:14Z archie $
 */
public interface Field2Desc {
	ByteFieldDesc genDesc(Field field);
}
