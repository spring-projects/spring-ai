/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.openai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.ai.embedding.EmbeddingOptions;

/**
 * @author Christian Tzolov
 * @since 0.8.0
 */
@JsonInclude(Include.NON_NULL)
public interface OpenAiEmbeddingOptions extends EmbeddingOptions {

	/**
	 * ID of the model to use.
	 */
	@JsonProperty("model")
	String getModel();

	/**
	 * The format to return the embeddings in. Can be either float or base64.
	 */
	@JsonProperty("encoding_format")
	String getEncodingFormat();

	/**
	 * A unique identifier representing your end-user, which can help OpenAI to monitor
	 * and detect abuse.
	 */
	@JsonProperty("user")
	String getUser();

}
