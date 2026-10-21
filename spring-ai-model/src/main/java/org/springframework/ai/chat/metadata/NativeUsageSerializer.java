/*
 * Copyright 2023-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.chat.metadata;

import java.util.function.Consumer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.jsontype.TypeSerializer;
import tools.jackson.databind.util.TokenBuffer;

/**
 * Jackson serializer for {@link DefaultUsage#getNativeUsage()} that never fails.
 * <p>
 * The native usage object is an opaque, provider-specific type (an SDK model class, a
 * protobuf message, ...) that is not guaranteed to be serializable by Jackson. This
 * serializer first writes the value into a {@link TokenBuffer}. If that succeeds, the
 * buffered tokens are copied to the target generator, producing exactly the output the
 * default serializer would have produced. If it fails, the value's {@code toString()}
 * representation is written instead, so that a non-serializable native usage object can
 * never break serialization of the enclosing {@code ChatResponse}.
 * <p>
 * The same applies when the mapper writes type information (for example with default
 * typing enabled): the value is written together with its type id, or as a plain string
 * if that fails.
 *
 * @author Soby Chacko
 * @since 2.0.2
 */
class NativeUsageSerializer extends ValueSerializer<Object> {

	private static final Log logger = LogFactory.getLog(NativeUsageSerializer.class);

	@Override
	public void serialize(Object value, JsonGenerator gen, SerializationContext ctxt) {
		serializeOrFallback(value, gen, ctxt, buffer -> ctxt.writeValue(buffer, value));
	}

	@Override
	public void serializeWithType(Object value, JsonGenerator gen, SerializationContext ctxt, TypeSerializer typeSer) {
		serializeOrFallback(value, gen, ctxt,
				buffer -> ctxt.findValueSerializer(value.getClass()).serializeWithType(value, buffer, ctxt, typeSer));
	}

	private void serializeOrFallback(Object value, JsonGenerator gen, SerializationContext ctxt,
			Consumer<TokenBuffer> writer) {
		TokenBuffer buffer = new TokenBuffer(ctxt, false);
		try {
			writer.accept(buffer);
		}
		catch (RuntimeException ex) {
			if (logger.isDebugEnabled()) {
				logger.debug("Native usage of type " + value.getClass().getName()
						+ " is not JSON serializable, falling back to toString()", ex);
			}
			gen.writeString(String.valueOf(value));
			return;
		}
		buffer.serialize(gen);
	}

}
