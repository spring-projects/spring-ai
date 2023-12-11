/*
 * Copyright 2023-2023 the original author or authors.
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
package org.springframework.ai.vectorstore;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

/**
 * Adds simple serialization/deserialization to the data stored in the InMemoryVectorStore
 */
public class SimplePersistentVectorStore extends InMemoryVectorStore {

	private static final Logger logger = LoggerFactory.getLogger(SimplePersistentVectorStore.class);

	public SimplePersistentVectorStore(EmbeddingClient embeddingClient) {
		super(embeddingClient);
	}

	public void save(File file) {
		String json = getVectorDbAsJson();
		try {
			if (!file.exists()) {
				logger.info("Creating new vector store file: " + file);
				file.createNewFile();
			}
			else {
				logger.info("Replacing existing vector store file: " + file);
				file.delete();
				file.createNewFile();
			}
		}
		catch (IOException ex) {
			throw new RuntimeException(ex);
		}
		try (OutputStream stream = new FileOutputStream(file)) {
			StreamUtils.copy(json, Charset.forName("UTF-8"), stream);
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public void load(File file) {
		TypeReference<HashMap<String, Document>> typeRef = new TypeReference<>() {
		};
		ObjectMapper objectMapper = new ObjectMapper();
		try {
			Map<String, Document> deserializedMap = objectMapper.readValue(file, typeRef);
			this.store = deserializedMap;
		}
		catch (IOException ex) {
			throw new RuntimeException(ex);
		}
	}

	public void load(Resource resource) {
		TypeReference<HashMap<String, Document>> typeRef = new TypeReference<>() {
		};
		ObjectMapper objectMapper = new ObjectMapper();
		try {
			Map<String, Document> deserializedMap = objectMapper.readValue(resource.getInputStream(), typeRef);
			this.store = deserializedMap;
		}
		catch (IOException ex) {
			throw new RuntimeException(ex);
		}
	}

	private String getVectorDbAsJson() {
		ObjectMapper objectMapper = new ObjectMapper();
		ObjectWriter objectWriter = objectMapper.writerWithDefaultPrettyPrinter();
		String json;
		try {
			json = objectWriter.writeValueAsString(this.store);
		}
		catch (JsonProcessingException e) {
			throw new RuntimeException("Error serializing documentMap to JSON.", e);
		}
		return json;
	}

}
