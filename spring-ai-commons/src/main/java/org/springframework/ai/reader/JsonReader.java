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

package org.springframework.ai.reader;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.StreamSupport;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.core.io.Resource;

/**
 * A class that reads JSON documents and converts them into a list of {@link Document}
 * objects.
 *
 * @author Mark Pollack
 * @author Christian Tzolov
 * @author rivkode rivkode
 * @since 1.0.0
 */
public class JsonReader implements DocumentReader {

	private final Resource resource;

	private final JsonMetadataGenerator jsonMetadataGenerator;

	private final ObjectMapper objectMapper = new ObjectMapper();

	/**
	 * The key from the JSON that we will use as the text to parse into the Document text
	 */
	private final List<String> jsonKeysToUse;

	public JsonReader(Resource resource) {
		this(resource, new String[0]);
	}

	public JsonReader(Resource resource, String... jsonKeysToUse) {
		this(resource, new EmptyJsonMetadataGenerator(), jsonKeysToUse);
	}

	public JsonReader(Resource resource, JsonMetadataGenerator jsonMetadataGenerator, String... jsonKeysToUse) {
		Objects.requireNonNull(jsonKeysToUse, "keys must not be null");
		Objects.requireNonNull(jsonMetadataGenerator, "jsonMetadataGenerator must not be null");
		Objects.requireNonNull(resource, "The Spring Resource must not be null");
		this.resource = resource;
		this.jsonMetadataGenerator = jsonMetadataGenerator;
		this.jsonKeysToUse = List.of(jsonKeysToUse);
	}

	@Override
	public List<Document> get() {
		try {
			JsonNode rootNode = this.objectMapper.readTree(this.resource.getInputStream());

			if (rootNode.isArray()) {
				return StreamSupport.stream(rootNode.spliterator(), true)
					.map(jsonNode -> parseJsonNode(jsonNode, this.objectMapper))
					.toList();
			}
			else {
				return Collections.singletonList(parseJsonNode(rootNode, this.objectMapper));
			}
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private Document parseJsonNode(JsonNode jsonNode, ObjectMapper objectMapper) {
		Map<String, Object> item = objectMapper.convertValue(jsonNode, new TypeReference<>() {

		});
		var sb = new StringBuilder();

		this.jsonKeysToUse.stream()
			.filter(item::containsKey)
			.forEach(key -> sb.append(key).append(": ").append(item.get(key)).append(System.lineSeparator()));

		Map<String, Object> metadata = this.jsonMetadataGenerator.generate(item);
		String content = sb.isEmpty() ? item.toString() : sb.toString();
		return new Document(content, metadata);
	}

	protected List<Document> get(JsonNode rootNode) {
		if (rootNode.isArray()) {
			return StreamSupport.stream(rootNode.spliterator(), true)
				.map(jsonNode -> parseJsonNode(jsonNode, this.objectMapper))
				.toList();
		}
		else {
			return Collections.singletonList(parseJsonNode(rootNode, this.objectMapper));
		}
	}

	/**
	 * Retrieves documents from the JSON resource using a JSON Pointer.
	 * @param pointer A JSON Pointer string (RFC 6901) to locate the desired element
	 * @return A list of Documents parsed from the located JSON element
	 * @throws RuntimeException if the JSON cannot be parsed or the pointer is invalid
	 */
	public List<Document> get(String pointer) {
		try {
			JsonNode rootNode = this.objectMapper.readTree(this.resource.getInputStream());
			JsonNode targetNode = rootNode.at(pointer);

			if (targetNode.isMissingNode()) {
				throw new IllegalArgumentException("Invalid JSON Pointer: " + pointer);
			}

			return get(targetNode);
		}
		catch (IOException e) {
			throw new RuntimeException("Error reading JSON resource", e);
		}
	}

}
