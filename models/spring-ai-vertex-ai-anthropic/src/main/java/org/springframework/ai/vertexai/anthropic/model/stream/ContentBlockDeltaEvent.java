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
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Event for content block delta.
 *
 * @author Alessio Bertazzo
 * @since 1.0.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContentBlockDeltaEvent(
// @formatter:off
									 @JsonProperty("type") EventType type,
									 @JsonProperty("index") Integer index,
									 @JsonProperty("delta") ContentBlockDeltaBody delta) implements StreamEvent {
	// @formatter:on

	@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type",
			visible = true)
	@JsonSubTypes({ @JsonSubTypes.Type(value = ContentBlockDeltaText.class, name = "text_delta"),
			@JsonSubTypes.Type(value = ContentBlockDeltaJson.class, name = "input_json_delta") })
	public interface ContentBlockDeltaBody {

		String type();

	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ContentBlockDeltaText(@JsonProperty("type") String type,
			@JsonProperty("text") String text) implements ContentBlockDeltaBody {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ContentBlockDeltaJson(@JsonProperty("type") String type,
			@JsonProperty("partial_json") String partialJson) implements ContentBlockDeltaBody {
	}
}
