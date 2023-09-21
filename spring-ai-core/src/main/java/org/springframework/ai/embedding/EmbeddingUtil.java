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

package org.springframework.ai.embedding;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import org.springframework.core.io.DefaultResourceLoader;

/**
 * @author Christian Tzolov
 */
public class EmbeddingUtil {

	private static Map<String, Integer> KNOWN_EMBEDDING_DIMENSIONS = loadKnownModelDimensions();

	/**
	 * Return the dimension of the requested embedding model name. If the model name is
	 * unknown uses the EmbeddingClient to perform a dummy EmbeddingClient#embed and count
	 * the response dimensions.
	 * @param embeddingClient Fall-back client to determine, empirically the dimensions.
	 * @param modelName Embedding model name to retrieve the dimensions for.
	 * @return Returns the embedding dimensions for the modelName.
	 */
	public static int dimensions(EmbeddingClient embeddingClient, String modelName) {

		if (KNOWN_EMBEDDING_DIMENSIONS.containsKey(modelName)) {
			// Retrieve the dimension from a pre-configured file.
			return KNOWN_EMBEDDING_DIMENSIONS.get(modelName);
		}
		else {
			// Determine the dimensions empirically.
			// Generate an embedding and count the dimension size;
			return embeddingClient.embed("Test String").size();
		}
	}

	private static Map<String, Integer> loadKnownModelDimensions() {
		try {
			Properties properties = new Properties();
			properties.load(new DefaultResourceLoader()
				.getResource("classpath:/embedding/embedding-model-dimensions.properties")
				.getInputStream());
			return properties.entrySet()
				.stream()
				.collect(Collectors.toMap(e -> e.getKey().toString(), e -> Integer.parseInt(e.getValue().toString())));
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

}
