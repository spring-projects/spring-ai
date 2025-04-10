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

package org.springframework.ai.chat.client.advisor.api;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.model.ChatResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link AdvisedResponse}.
 *
 * @author Thomas Vitale
 */
class AdvisedResponseTests {

	@Test
	void buildAdvisedResponse() {
		AdvisedResponse advisedResponse = new AdvisedResponse(mock(ChatResponse.class), Map.of());
		assertThat(advisedResponse).isNotNull();
	}

	@Test
	void whenAdviseContextIsNullThenThrows() {
		assertThatThrownBy(() -> new AdvisedResponse(mock(ChatResponse.class), null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("adviseContext cannot be null");
	}

	@Test
	void whenAdviseContextKeysIsNullThenThrows() {
		Map<String, Object> adviseContext = new HashMap<>();
		adviseContext.put(null, "value");
		assertThatThrownBy(() -> new AdvisedResponse(mock(ChatResponse.class), adviseContext))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("adviseContext keys cannot be null");
	}

	@Test
	void whenAdviseContextValuesIsNullThenThrows() {
		Map<String, Object> adviseContext = new HashMap<>();
		adviseContext.put("key", null);
		assertThatThrownBy(() -> new AdvisedResponse(mock(ChatResponse.class), adviseContext))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("adviseContext values cannot be null");
	}

	@Test
	void whenBuildFromNullAdvisedResponseThenThrows() {
		assertThatThrownBy(() -> AdvisedResponse.from(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("advisedResponse cannot be null");
	}

	@Test
	void buildFromAdvisedResponse() {
		AdvisedResponse advisedResponse = new AdvisedResponse(mock(ChatResponse.class), Map.of());
		AdvisedResponse.Builder builder = AdvisedResponse.from(advisedResponse);
		assertThat(builder).isNotNull();
	}

	@Test
	void whenUpdateFromNullContextThenThrows() {
		AdvisedResponse advisedResponse = new AdvisedResponse(mock(ChatResponse.class), Map.of());
		assertThatThrownBy(() -> advisedResponse.updateContext(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("contextTransform cannot be null");
	}

}
