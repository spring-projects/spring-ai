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
package org.springframework.ai.vertexai.anthropic.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Input messages.
 *
 * Our models are trained to operate on alternating user and assistant conversational
 * turns. When creating a new Message, you specify the prior conversational turns with the
 * messages parameter, and the model then generates the next Message in the conversation.
 * Each input message must be an object with a role and content. You can specify a single
 * user-role message, or you can include multiple user and assistant messages. The first
 * message must always use the user role. If the final message uses the assistant role,
 * the response content will continue immediately from the content in that message. This
 * can be used to constrain part of the model's response.
 *
 * @param content The contents of the message. Can be of one of String or
 * MultiModalContent.
 * @param role The role of the messages author. Could be one of the {@link Role} types.
 * @author Alessio Bertazzo
 * @since 1.0.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AnthropicMessage(
// @formatter:off
								@JsonProperty("content") List<ContentBlock> content,
								@JsonProperty("role") Role role) {
	// @formatter:on
}
