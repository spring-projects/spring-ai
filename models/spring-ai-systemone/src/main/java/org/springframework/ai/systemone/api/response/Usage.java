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

package org.springframework.ai.systemone.api.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

/**
 * Token consumption of one request. The state is processed once for the whole request, so
 * adding questions costs far less than issuing one call per question.
 *
 * @param inputTokens tokens consumed by the state and the questions
 * @param outputTokens tokens consumed producing the answers
 * @author Christian Tzolov
 * @since 2.1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Usage(@JsonProperty("input_tokens") @Nullable Integer inputTokens,
		@JsonProperty("output_tokens") @Nullable Integer outputTokens) {

	/** A usage record with no counts reported. */
	public static final Usage EMPTY = new Usage(null, null);

	/**
	 * @return the sum of input and output tokens, counting an absent count as zero
	 */
	public int totalTokens() {
		return (this.inputTokens == null ? 0 : this.inputTokens) + (this.outputTokens == null ? 0 : this.outputTokens);
	}
}
