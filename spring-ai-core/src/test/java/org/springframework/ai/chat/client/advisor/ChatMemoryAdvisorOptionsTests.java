/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.chat.client.advisor;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.DefaultChatClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.*;

/**
 * @author Jonghoon Park
 */
public class ChatMemoryAdvisorOptionsTests {

	@Test
	public void testChatMemoryAdvisorConfigurator() {
		DefaultChatClient.DefaultAdvisorSpec spec = new DefaultChatClient.DefaultAdvisorSpec();
		spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, 1);
		spec.param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 100);

		DefaultChatClient.DefaultAdvisorSpec spec2 = new DefaultChatClient.DefaultAdvisorSpec();
		ChatMemoryAdvisorOptions options = ChatMemoryAdvisorOptions.builder()
			.conversationId(1)
			.retrieveSize(100)
			.build();
		options.applyTo(spec2);

		assertEquals(spec.getParams().get(CHAT_MEMORY_CONVERSATION_ID_KEY),
				spec2.getParams().get(CHAT_MEMORY_CONVERSATION_ID_KEY));
		assertEquals(spec.getParams().get(CHAT_MEMORY_RETRIEVE_SIZE_KEY),
				spec2.getParams().get(CHAT_MEMORY_RETRIEVE_SIZE_KEY));
	}

}
