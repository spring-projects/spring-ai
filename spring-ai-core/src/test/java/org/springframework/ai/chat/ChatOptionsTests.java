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
package org.springframework.ai.chat;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.ChatOptionsBuilder;

/**
 * Unit Tests for {@link ChatOptions}.
 *
 * @author youngmon
 * @since 0.8.1
 */
public class ChatOptionsTests {

	@Test
	void createNewChatOptionsTest() {
		Float temperature = 1.1f;
		Float topP = 2.2f;
		Integer topK = 111;

		ChatOptions options = ChatOptionsBuilder.builder()
			.withTemperature(temperature)
			.withTopK(topK)
			.withTopP(topP)
			.build();

		assertThat(options.getTemperature()).isEqualTo(temperature);
		assertThat(options.getTopP()).isEqualTo(topP);
		assertThat(options.getTopK()).isEqualTo(topK);
	}

	@Test
	void duplicateChatOptionsTest() {
		Float initTemperature = 1.1f;
		Float initTopP = 2.2f;
		Integer initTopK = 111;

		ChatOptions options = ChatOptionsBuilder.builder()
			.withTemperature(initTemperature)
			.withTopP(initTopP)
			.withTopK(initTopK)
			.build();

		ChatOptions options1 = ChatOptionsBuilder.builder(options).build();

		assertThat(options.getTopP()).isEqualTo(options1.getTopP());
		assertThat(options.getTopK()).isEqualTo(options1.getTopK());
		assertThat(options.getTemperature()).isEqualTo(options1.getTemperature());

		assertThat(options).isNotSameAs(options1);
	}

}