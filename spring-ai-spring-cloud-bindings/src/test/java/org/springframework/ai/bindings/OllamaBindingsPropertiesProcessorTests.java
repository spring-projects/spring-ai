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
 * Unit tests for {@link OllamaBindingsPropertiesProcessor}.
 *
 * @author Thomas Vitale
 */
class OllamaBindingsPropertiesProcessorTests {

	private final Bindings bindings = new Bindings(new Binding("test-name", Paths.get("test-path"),
	// @formatter:off
            Map.of(
                Binding.TYPE, OllamaBindingsPropertiesProcessor.TYPE,
                "uri", "https://example.net/ollama:11434"
            )));
    // @formatter:on

	private final MockEnvironment environment = new MockEnvironment();

	private final Map<String, Object> properties = new HashMap<>();

	@Test
	void propertiesAreContributed() {
		new OllamaBindingsPropertiesProcessor().process(environment, bindings, properties);
		assertThat(properties).containsEntry("spring.ai.ollama.base-url", "https://example.net/ollama:11434");
	}

	@Test
	void whenDisabledThenPropertiesAreNotContributed() {
		environment.setProperty("%s.ollama.enabled".formatted(CONFIG_PATH), "false");

		new OllamaBindingsPropertiesProcessor().process(environment, bindings, properties);
		assertThat(properties).isEmpty();
	}

}
