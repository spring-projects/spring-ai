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

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The evnt type of the streamed chunk.
 *
 * @author Alessio Bertazzo
 * @since 1.0.0
 */
public enum EventType {

	/**
	 * Message start event. Contains a Message object with empty content.
	 */
	@JsonProperty("message_start")
	MESSAGE_START,

	/**
	 * Message delta event, indicating top-level changes to the final Message object.
	 */
	@JsonProperty("message_delta")
	MESSAGE_DELTA,

	/**
	 * A final message stop event.
	 */
	@JsonProperty("message_stop")
	MESSAGE_STOP,

	/**
	 *
	 */
	@JsonProperty("content_block_start")
	CONTENT_BLOCK_START,

	/**
	 *
	 */
	@JsonProperty("content_block_delta")
	CONTENT_BLOCK_DELTA,

	/**
	 *
	 */
	@JsonProperty("content_block_stop")
	CONTENT_BLOCK_STOP,

	/**
	 *
	 */
	@JsonProperty("error")
	ERROR,

	/**
	 *
	 */
	@JsonProperty("ping")
	PING,

	/**
	 * Artifically created event to aggregate tool use events.
	 */
	TOOL_USE_AGGREGATE;

}
