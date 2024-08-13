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
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * A stream event.
 *
 * @author Alessio Bertazzo
 * @since 1.0.0
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type",
		visible = true)
@JsonSubTypes({ @JsonSubTypes.Type(value = ContentBlockStartEvent.class, name = "content_block_start"),
		@JsonSubTypes.Type(value = ContentBlockDeltaEvent.class, name = "content_block_delta"),
		@JsonSubTypes.Type(value = ContentBlockStopEvent.class, name = "content_block_stop"),
		@JsonSubTypes.Type(value = PingEvent.class, name = "ping"),
		@JsonSubTypes.Type(value = ErrorEvent.class, name = "error"),
		@JsonSubTypes.Type(value = MessageStartEvent.class, name = "message_start"),
		@JsonSubTypes.Type(value = MessageDeltaEvent.class, name = "message_delta"),
		@JsonSubTypes.Type(value = MessageStopEvent.class, name = "message_stop") })
public interface StreamEvent {

	@JsonProperty("type")
	EventType type();

}