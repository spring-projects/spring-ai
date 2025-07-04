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

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TextMessageFormatter}.
 *
 * @author tingchuan.li
 */
class TextMessageFormatterTest {

	@Test
	void format() {
		String text = "Hello World!";
		Message message = new UserMessage(text);
		MessageFormatter formatter = new TextMessageFormatter();
		assertThat(formatter.format(message)).isEqualTo(text);
	}

}
