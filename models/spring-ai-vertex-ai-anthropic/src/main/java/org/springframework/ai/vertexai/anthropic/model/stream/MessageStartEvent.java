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
package org.springframework.ai.vertexai.anthropic.model.stream;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.ai.vertexai.anthropic.model.ChatCompletionResponse;

/**
 * A message start event.
 *
 * @author Alessio Bertazzo
 * @since 1.0.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MessageStartEvent(
// @formatter:off
								@JsonProperty("type") EventType type,
								@JsonProperty("message") ChatCompletionResponse message) implements StreamEvent {
	// @formatter:on
}
