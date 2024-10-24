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
import static org.springframework.ai.bindings.BindingsValidator.CONFIG_PATH;

/**
 * Unit tests for {@link TanzuBindingsPropertiesProcessor}.
 *
 * @author Stuart Charlton
 */
class TanzuBindingsPropertiesProcessorTests {

	private final Bindings bindings = new Bindings(new Binding("test-name", Paths.get("test-path"),
	// @formatter:off
            Map.of(
                    Binding.TYPE, TanzuBindingsPropertiesProcessor.TYPE,
                    "api-key", "demo",
                    "uri", "https://my.openai.example.net",
					"model-name", "llava1.6",
					"model-capabilities", " chat , vision "
            )),
			new Binding("test-name2", Paths.get("test-path2"),
			Map.of(
				Binding.TYPE, TanzuBindingsPropertiesProcessor.TYPE,
				"api-key", "demo2",
				"uri", "https://my.openai2.example.net",
				"model-name", "text-embed-large",
				"model-capabilities", "embedding")));
    // @formatter:on

	private final Bindings bindingsMissingModelCapabilities = new Bindings(
			new Binding("test-name", Paths.get("test-path"),
			// @formatter:off
            Map.of(
                    Binding.TYPE, TanzuBindingsPropertiesProcessor.TYPE,
                    "api-key", "demo",
                    "uri", "https://my.openai.example.net"
            )));
    // @formatter:on

	private final MockEnvironment environment = new MockEnvironment();

	private final Map<String, Object> properties = new HashMap<>();

	@Test
	void propertiesAreContributed() {
		new TanzuBindingsPropertiesProcessor().process(this.environment, this.bindings, this.properties);
		assertThat(this.properties).containsEntry("spring.ai.openai.chat.api-key", "demo");
		assertThat(this.properties).containsEntry("spring.ai.openai.chat.base-url", "https://my.openai.example.net");
		assertThat(this.properties).containsEntry("spring.ai.openai.chat.options.model", "llava1.6");
		assertThat(this.properties).containsEntry("spring.ai.openai.embedding.api-key", "demo2");
		assertThat(this.properties).containsEntry("spring.ai.openai.embedding.base-url",
				"https://my.openai2.example.net");
		assertThat(this.properties).containsEntry("spring.ai.openai.embedding.options.model", "text-embed-large");
	}

	@Test
	void propertiesAreMissingModelCapabilities() {
		new TanzuBindingsPropertiesProcessor().process(this.environment, this.bindingsMissingModelCapabilities,
				this.properties);
		assertThat(this.properties).isEmpty();
	}

	@Test
	void whenDisabledThenPropertiesAreNotContributed() {
		this.environment.setProperty("%s.genai.enabled".formatted(CONFIG_PATH), "false");

		new TanzuBindingsPropertiesProcessor().process(this.environment, this.bindings, this.properties);
		assertThat(this.properties).isEmpty();
	}

}
