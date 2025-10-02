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

	@Test
	void emptyBindingsShouldNotThrowException() {
		Bindings emptyBindings = new Bindings();

		new MistralAiBindingsPropertiesProcessor().process(this.environment, emptyBindings, this.properties);

		assertThat(this.properties).isEmpty();
	}

	@Test
	void onlyUriWithoutApiKeyShouldSetBothProperties() {
		Bindings bindingsWithOnlyUri = new Bindings(new Binding("test-name", Paths.get("test-path"), Map
			.of(Binding.TYPE, MistralAiBindingsPropertiesProcessor.TYPE, "uri", "https://custom.mistralai.com")));

		new MistralAiBindingsPropertiesProcessor().process(this.environment, bindingsWithOnlyUri, this.properties);

		assertThat(this.properties).containsEntry("spring.ai.mistralai.base-url", "https://custom.mistralai.com");
		assertThat(this.properties).containsEntry("spring.ai.mistralai.api-key", null);
	}

	@Test
	void onlyApiKeyWithoutUriShouldSetBothProperties() {
		Bindings bindingsWithOnlyApiKey = new Bindings(new Binding("test-name", Paths.get("test-path"),
				Map.of(Binding.TYPE, MistralAiBindingsPropertiesProcessor.TYPE, "api-key", "secret-key")));

		new MistralAiBindingsPropertiesProcessor().process(this.environment, bindingsWithOnlyApiKey, this.properties);

		assertThat(this.properties).containsEntry("spring.ai.mistralai.api-key", "secret-key");
		assertThat(this.properties).containsEntry("spring.ai.mistralai.base-url", null);
	}

	@Test
	void extraPropertiesAreIgnored() {
		Bindings extraPropsBinding = new Bindings(new Binding("test-name", Paths.get("test-path"),
				Map.of(Binding.TYPE, MistralAiBindingsPropertiesProcessor.TYPE, "api-key", "demo", "uri",
						"https://mistralai.example.com", "extra-property", "should-be-ignored", "another-prop",
						"also-ignored")));

		new MistralAiBindingsPropertiesProcessor().process(this.environment, extraPropsBinding, this.properties);

		assertThat(this.properties).hasSize(2);
		assertThat(this.properties).containsEntry("spring.ai.mistralai.api-key", "demo");
		assertThat(this.properties).containsEntry("spring.ai.mistralai.base-url", "https://mistralai.example.com");
		assertThat(this.properties).doesNotContainKey("spring.ai.mistralai.extra-property");
	}

	@Test
	void existingPropertiesAreOverwritten() {
		this.properties.put("spring.ai.mistralai.api-key", "old-key");
		this.properties.put("spring.ai.mistralai.base-url", "https://old.example.com");

		new MistralAiBindingsPropertiesProcessor().process(this.environment, this.bindings, this.properties);

		assertThat(this.properties).containsEntry("spring.ai.mistralai.api-key", "demo");
		assertThat(this.properties).containsEntry("spring.ai.mistralai.base-url", "https://my.mistralai.example.net");
	}

	@Test
	void bindingWithDifferentKeyNamesAreIgnored() {
		// Using different key names (not "api-key" and "uri")
		Bindings wrongKeysBinding = new Bindings(new Binding("test-name", Paths.get("test-path"),
				Map.of(Binding.TYPE, MistralAiBindingsPropertiesProcessor.TYPE, "apiKey", "demo", // Wrong
																									// key
																									// name
																									// (camelCase)
						"url", "https://mistralai.example.com"))); // Wrong key name

		new MistralAiBindingsPropertiesProcessor().process(this.environment, wrongKeysBinding, this.properties);

		// Should set null for missing expected keys
		assertThat(this.properties).containsEntry("spring.ai.mistralai.api-key", null);
		assertThat(this.properties).containsEntry("spring.ai.mistralai.base-url", null);
	}

	@Test
	void multipleBindingsWithMistralAiTypeShouldProcessLast() {
		Binding mistralBinding1 = new Binding("mistral-1", Paths.get("path-1"), Map.of(Binding.TYPE,
				MistralAiBindingsPropertiesProcessor.TYPE, "api-key", "key1", "uri", "https://mistral1.example.com"));

		Binding mistralBinding2 = new Binding("mistral-2", Paths.get("path-2"), Map.of(Binding.TYPE,
				MistralAiBindingsPropertiesProcessor.TYPE, "api-key", "key2", "uri", "https://mistral2.example.com"));

		Bindings multipleBindings = new Bindings(mistralBinding1, mistralBinding2);

		new MistralAiBindingsPropertiesProcessor().process(this.environment, multipleBindings, this.properties);

		// Should process the last matching binding (overrides previous)
		assertThat(this.properties).containsEntry("spring.ai.mistralai.api-key", "key2");
		assertThat(this.properties).containsEntry("spring.ai.mistralai.base-url", "https://mistral2.example.com");
	}

}
