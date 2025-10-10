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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link MistralAiBindingsPropertiesProcessor}.
 *
 * @author Thomas Vitale
 */
class MistralAiBindingsPropertiesProcessorTests {

	private final Bindings bindings = new Bindings(new Binding("test-name", Paths.get("test-path"),
	// @formatter:off
			Map.of(
				Binding.TYPE, MistralAiBindingsPropertiesProcessor.TYPE,
				"api-key", "demo",
				"uri", "https://my.mistralai.example.net"
			)));
    // @formatter:on

	private final MockEnvironment environment = new MockEnvironment();

	private final Map<String, Object> properties = new HashMap<>();

	@Test
	void propertiesAreContributed() {
		new MistralAiBindingsPropertiesProcessor().process(this.environment, this.bindings, this.properties);
		assertThat(this.properties).containsEntry("spring.ai.mistralai.api-key", "demo");
		assertThat(this.properties).containsEntry("spring.ai.mistralai.base-url", "https://my.mistralai.example.net");
	}

	@Test
	void whenDisabledThenPropertiesAreNotContributed() {
		this.environment.setProperty(
				"%s.mistralai.enabled".formatted(org.springframework.ai.bindings.BindingsValidator.CONFIG_PATH),
				"false");

		new MistralAiBindingsPropertiesProcessor().process(this.environment, this.bindings, this.properties);
		assertThat(this.properties).isEmpty();
	}

	@Test
	void nullBindingsShouldThrowException() {
		assertThatThrownBy(
				() -> new MistralAiBindingsPropertiesProcessor().process(this.environment, null, this.properties))
			.isInstanceOf(NullPointerException.class);
	}

	@Test
	void nullEnvironmentShouldThrowException() {
		assertThatThrownBy(
				() -> new MistralAiBindingsPropertiesProcessor().process(null, this.bindings, this.properties))
			.isInstanceOf(NullPointerException.class);
	}

	@Test
	void nullPropertiesShouldThrowException() {
		assertThatThrownBy(
				() -> new MistralAiBindingsPropertiesProcessor().process(this.environment, this.bindings, null))
			.isInstanceOf(NullPointerException.class);
	}

	@Test
	void missingApiKeyShouldStillSetNullValue() {
		Bindings bindingsWithoutApiKey = new Bindings(new Binding("test-name", Paths.get("test-path"), Map
			.of(Binding.TYPE, MistralAiBindingsPropertiesProcessor.TYPE, "uri", "https://my.mistralai.example.net")));

		new MistralAiBindingsPropertiesProcessor().process(this.environment, bindingsWithoutApiKey, this.properties);

		assertThat(this.properties).containsEntry("spring.ai.mistralai.base-url", "https://my.mistralai.example.net");
		assertThat(this.properties).containsEntry("spring.ai.mistralai.api-key", null);
	}

	@Test
	void emptyApiKeyIsStillSet() {
		Bindings bindingsWithEmptyApiKey = new Bindings(new Binding("test-name", Paths.get("test-path"),
				Map.of(Binding.TYPE, MistralAiBindingsPropertiesProcessor.TYPE, "api-key", "", "uri",
						"https://my.mistralai.example.net")));

		new MistralAiBindingsPropertiesProcessor().process(this.environment, bindingsWithEmptyApiKey, this.properties);

		assertThat(this.properties).containsEntry("spring.ai.mistralai.api-key", "");
		assertThat(this.properties).containsEntry("spring.ai.mistralai.base-url", "https://my.mistralai.example.net");
	}

	@Test
	void wrongBindingTypeShouldBeIgnored() {
		Bindings wrongTypeBindings = new Bindings(new Binding("test-name", Paths.get("test-path"),
				Map.of(Binding.TYPE, "different-type", "api-key", "demo", "uri", "https://my.mistralai.example.net")));

		new MistralAiBindingsPropertiesProcessor().process(this.environment, wrongTypeBindings, this.properties);

		assertThat(this.properties).isEmpty();
	}

}
