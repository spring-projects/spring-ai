/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.chat.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ChatClientResponse}.
 *
 * @author Thomas Vitale
 */
class ChatClientResponseTests {


	@Test
	void whenCopyThenImmutableContext() {
		Map<String, Object> context = new HashMap<>();
		context.put("key", "value");
		ChatResponse chatResponse=new ChatResponse(new ArrayList<>(),new ChatResponseMetadata(), context);
		ChatClientResponse response = ChatClientResponse.builder().chatResponse(chatResponse).build();

		ChatClientResponse copy = response.copy();

		copy.chatResponse().getContext().put("key2", "value2");
		assertThat(response.chatResponse().getContext()).doesNotContainKey("key2");
		assertThat(copy.chatResponse().getContext()).containsKey("key2");

		copy.chatResponse().getContext().put("key", "newValue");
		assertThat(copy.chatResponse().getContext()).containsEntry("key", "newValue");
		assertThat(response.chatResponse().getContext()).containsEntry("key", "value");
	}

	@Test
	void whenMutateThenImmutableContext() {
		Map<String, Object> context = new HashMap<>();
		context.put("key", "value");
		ChatResponse chatResponse=new ChatResponse(new ArrayList<>(),new ChatResponseMetadata(),context);
		ChatClientResponse response = ChatClientResponse.builder().chatResponse(chatResponse).build();
		HashMap<String,Object> hashMap=new HashMap<>();
		hashMap.put("key2","value");
		ChatClientResponse copy = response.mutate().context(hashMap).build();

		assertThat(response.chatResponse().getContext()).doesNotContainKey("key2");
		assertThat(copy.chatResponse().getContext()).containsKey("key2");

		copy.chatResponse().getContext().put("key", "newValue");
		assertThat(copy.chatResponse().getContext()).containsEntry("key", "newValue");
		assertThat(response.chatResponse().getContext()).containsEntry("key", "value");
	}

}
