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

package org.springframework.ai.bindings;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.cloud.bindings.Binding;
import org.springframework.cloud.bindings.Bindings;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link OpenAiBindingsPropertiesProcessor}.
 *
 * @author Thomas Vitale
 */
class OpenAiBindingsPropertiesProcessorTests {

	private final Bindings bindings = new Bindings(new Binding("test-name", Paths.get("test-path"),
	// @formatter:off
			Map.of(
				Binding.TYPE, OpenAiBindingsPropertiesProcessor.TYPE,
				"api-key", "demo",
				"uri", "https://my.openai.example.net"
			)));
    // @formatter:on

	private final MockEnvironment environment = new MockEnvironment();

	private final Map<String, Object> properties = new HashMap<>();

	@Test
	void propertiesAreContributed() {
		new OpenAiBindingsPropertiesProcessor().process(this.environment, this.bindings, this.properties);
		assertThat(this.properties).containsEntry("spring.ai.openai.api-key", "demo");
		assertThat(this.properties).containsEntry("spring.ai.openai.base-url", "https://my.openai.example.net");
	}

	@Test
	void whenDisabledThenPropertiesAreNotContributed() {
		this.environment.setProperty(
				"%s.openai.enabled".formatted(org.springframework.ai.bindings.BindingsValidator.CONFIG_PATH), "false");

		new OpenAiBindingsPropertiesProcessor().process(this.environment, this.bindings, this.properties);
		assertThat(this.properties).isEmpty();
	}

}
