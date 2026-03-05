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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.infinispan.protostream.MessageMarshaller;

/**
 * Marshaller to read and write embeddings to Infinispan
 */
public class SpringAiItemMarshaller implements MessageMarshaller<SpringAiInfinispanItem> {

	private final String typeName;

	/**
	 * Constructor for the SpringAiItemMarshaller Marshaller
	 * @param typeName, the full type of the protobuf entity
	 */
	public SpringAiItemMarshaller(String typeName) {
		this.typeName = typeName;
	}

	@Override
	public SpringAiInfinispanItem readFrom(ProtoStreamReader reader) throws IOException {
		String id = reader.readString("id");
		String text = reader.readString("text");
		Set<SpringAiMetadata> metadata = reader.readCollection("metadata", new HashSet<>(), SpringAiMetadata.class);
		float[] embedding = reader.readFloats("embedding");

		Map<String, Object> metadataMap = new HashMap<>();
		if (metadata != null) {
			for (SpringAiMetadata meta : metadata) {
				metadataMap.put(meta.name(), meta.value());
			}
		}
		return new SpringAiInfinispanItem(id, text, metadata, embedding, metadataMap);
	}

	@Override
	public void writeTo(ProtoStreamWriter writer, SpringAiInfinispanItem item) throws IOException {
		writer.writeString("id", item.id());
		writer.writeString("text", item.text());
		writer.writeCollection("metadata", item.metadata(), SpringAiMetadata.class);
		writer.writeFloats("embedding", item.embedding());
	}

	@Override
	public Class<? extends SpringAiInfinispanItem> getJavaClass() {
		return SpringAiInfinispanItem.class;
	}

	@Override
	public String getTypeName() {
		return this.typeName;
	}

}
