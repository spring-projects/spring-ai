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

import java.util.Map;

/**
 * Event that represents a change in the content of a block.
 *
 * @author Alessio Bertazzo
 * @since 1.0.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContentBlockStartEvent(
// @formatter:off
									 @JsonProperty("type") EventType type,
									 @JsonProperty("index") Integer index,
									 @JsonProperty("content_block") ContentBlockBody contentBlock) implements StreamEvent {
	// @formatter:on

	@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type",
			visible = true)
	@JsonSubTypes({ @JsonSubTypes.Type(value = ContentBlockToolUse.class, name = "tool_use"),
			@JsonSubTypes.Type(value = ContentBlockText.class, name = "text") })
	public interface ContentBlockBody {

		String type();

	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ContentBlockToolUse(@JsonProperty("type") String type, @JsonProperty("id") String id,
			@JsonProperty("name") String name,
			@JsonProperty("input") Map<String, Object> input) implements ContentBlockBody {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ContentBlockText(@JsonProperty("type") String type,
			@JsonProperty("text") String text) implements ContentBlockBody {
	}
}
