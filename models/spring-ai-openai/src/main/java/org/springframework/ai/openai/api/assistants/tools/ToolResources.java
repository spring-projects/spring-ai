/*
 * Copyright 2024 the original author or authors.
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

package org.springframework.ai.openai.api.assistants.tools;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import java.util.List;
import java.util.Map;

/**
 * Represents the resources that assist tools in performing specific actions, such as file
 * searching or code interpreting.
 *
 * @author Alexandros Pappas
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ToolResources(@JsonProperty("code_interpreter") CodeInterpreterResource codeInterpreter,
		@JsonProperty("file_search") FileSearchResource fileSearch) {

	/**
	 * Represents resources required for the Code Interpreter tool.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record CodeInterpreterResource(@JsonProperty("file_ids") List<String> fileIds) {
	}

	/**
	 * Represents resources required for the File Search tool.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record FileSearchResource(@JsonProperty("vector_store_ids") List<String> vectorStoreIds,
			@JsonProperty("vector_stores") List<VectorStore> vectorStores) {

		/**
		 * Represents a vector store configuration for file search.
		 */
		@JsonInclude(JsonInclude.Include.NON_NULL)
		public record VectorStore(@JsonProperty("file_ids") List<String> fileIds,
				@JsonProperty("chunking_strategy") ChunkingStrategy chunkingStrategy,
				@JsonProperty("metadata") Map<String, String> metadata) {

			/**
			 * Represents the chunking strategy used for vector stores.
			 */
			@JsonInclude(JsonInclude.Include.NON_NULL)
			@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
			@JsonSubTypes({ @JsonSubTypes.Type(value = AutoChunkingStrategy.class, name = "auto"),
					@JsonSubTypes.Type(value = StaticChunkingStrategy.class, name = "static") })
			public interface ChunkingStrategy {

				@JsonProperty("type")
				String type();

			}

			/**
			 * Represents the Auto Chunking Strategy.
			 */
			@JsonInclude(JsonInclude.Include.NON_NULL)
			public record AutoChunkingStrategy(@JsonProperty("type") String type) implements ChunkingStrategy {
			}

			/**
			 * Represents the Static Chunking Strategy.
			 */
			@JsonInclude(JsonInclude.Include.NON_NULL)
			public record StaticChunkingStrategy(@JsonProperty("type") String type, // Always
																					// "static"
					@JsonProperty("static") Static staticConfig) implements ChunkingStrategy {

				/**
				 * Represents the static configuration for the Static Chunking Strategy.
				 */
				@JsonInclude(JsonInclude.Include.NON_NULL)
				public record Static(@JsonProperty("max_chunk_size_tokens") Integer maxChunkSizeTokens,
						@JsonProperty("chunk_overlap_tokens") Integer chunkOverlapTokens) {
				}
			}
		}
	}
}
