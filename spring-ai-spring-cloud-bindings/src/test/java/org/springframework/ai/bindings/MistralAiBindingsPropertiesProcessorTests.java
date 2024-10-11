/*
 * Copyright 2024 the original author or authors.
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

package org.springframework.ai.bindings;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.bindings.Binding;
import org.springframework.cloud.bindings.Bindings;
import org.springframework.mock.env.MockEnvironment;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.ai.bindings.BindingsValidator.CONFIG_PATH;

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
		new MistralAiBindingsPropertiesProcessor().process(environment, bindings, properties);
		assertThat(properties).containsEntry("spring.ai.mistralai.api-key", "demo");
		assertThat(properties).containsEntry("spring.ai.mistralai.base-url", "https://my.mistralai.example.net");
	}

	@Test
	void whenDisabledThenPropertiesAreNotContributed() {
		environment.setProperty("%s.mistralai.enabled".formatted(CONFIG_PATH), "false");

		new MistralAiBindingsPropertiesProcessor().process(environment, bindings, properties);
		assertThat(properties).isEmpty();
	}

}
