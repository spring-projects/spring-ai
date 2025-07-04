/*
 * Copyright 2024-2025 the original author or authors.
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

package org.springframework.ai.chat.observation.trace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * format the message in order to have a pretty langfuse display
 *
 * @author tingchuan.li
 * @since 1.0.0
 */
public class LangfuseMessageFormatter implements MessageFormatter {

	private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();

	private static final String ROLE = "role";

	private static final String CONTENT = "content";

	@Override
	public String format(Message message) {
		try {
			if (message instanceof AssistantMessage && !StringUtils.hasText(message.getText())
					&& !CollectionUtils.isEmpty(((AssistantMessage) message).getToolCalls())) {
				// tool call request
				Map<String, Object> map = new HashMap<>();
				map.put(ROLE, message.getMessageType().getValue());
				map.put(CONTENT, ((AssistantMessage) message).getToolCalls());
				return OBJECT_MAPPER.writeValueAsString(map);
			}
			if (message instanceof ToolResponseMessage) {
				// tool call response
				Map<String, Object> map = new HashMap<>();
				map.put(ROLE, message.getMessageType().getValue());
				map.put(CONTENT, ((ToolResponseMessage) message).getResponses());
				return OBJECT_MAPPER.writeValueAsString(map);
			}
			Map<String, String> map = new HashMap<>();
			map.put(ROLE, message.getMessageType().getValue());
			map.put(CONTENT, message.getText());
			return OBJECT_MAPPER.writeValueAsString(map);
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}
