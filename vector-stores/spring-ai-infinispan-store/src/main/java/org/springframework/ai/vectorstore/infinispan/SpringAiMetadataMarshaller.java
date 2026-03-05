/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.vectorstore.infinispan;

import java.io.IOException;
import java.util.Date;

import org.infinispan.protostream.MessageMarshaller;

/**
 * Marshaller to read and write metadata to Infinispan
 */
public class SpringAiMetadataMarshaller implements MessageMarshaller<SpringAiMetadata> {

	private final String typeName;

	/**
	 * Constructor for the LangChainMetadata Marshaller
	 * @param typeName, the full type of the protobuf entity
	 */
	public SpringAiMetadataMarshaller(String typeName) {
		this.typeName = typeName;
	}

	@Override
	public SpringAiMetadata readFrom(ProtoStreamReader reader) throws IOException {
		String name = reader.readString("name");
		String valueStr = reader.readString("value");
		Long valueInt = reader.readLong("value_int");
		Double valueFloat = reader.readDouble("value_float");
		Boolean valueBoolean = reader.readBoolean("value_bool");
		Date valueDate = reader.readDate("value_date");

		Object value = valueStr;
		if (value == null) {
			value = valueInt;
		}
		if (value == null) {
			value = valueFloat;
		}
		if (value == null) {
			value = valueBoolean;
		}
		if (value == null) {
			value = valueDate;
		}

		return new SpringAiMetadata(name, value);
	}

	@Override
	public void writeTo(ProtoStreamWriter writer, SpringAiMetadata item) throws IOException {
		writer.writeString("name", item.name());
		String value = null;
		Long value_int = null;
		Double value_float = null;
		Boolean value_boolean = null;
		Date value_date = null;
		if (item.value() instanceof String) {
			value = (String) item.value();
		}
		else if (item.value() instanceof Integer) {
			value_int = ((Integer) item.value()).longValue();
		}
		else if (item.value() instanceof Long) {
			value_int = (Long) item.value();
		}
		else if (item.value() instanceof Float) {
			value_float = ((Float) item.value()).doubleValue();
		}
		else if (item.value() instanceof Double) {
			value_float = (Double) item.value();
		}
		else if (item.value() instanceof Boolean) {
			value_boolean = ((Boolean) item.value());
		}
		else if (item.value() instanceof Date) {
			value_date = ((Date) item.value());
		}
		else {
			value = item.value().toString();
		}

		writer.writeString("value", value);
		writer.writeLong("value_int", value_int);
		writer.writeDouble("value_float", value_float);
		writer.writeBoolean("value_bool", value_boolean);
		writer.writeDate("value_date", value_date);
	}

	@Override
	public Class<? extends SpringAiMetadata> getJavaClass() {
		return SpringAiMetadata.class;
	}

	@Override
	public String getTypeName() {
		return this.typeName;
	}

}
