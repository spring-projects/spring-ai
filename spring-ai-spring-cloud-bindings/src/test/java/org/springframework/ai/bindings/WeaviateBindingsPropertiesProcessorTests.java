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
 * Unit tests for {@link WeaviateBindingsPropertiesProcessor}.
 *
 * @author Thomas Vitale
 */
class WeaviateBindingsPropertiesProcessorTests {

	private final Bindings bindings = new Bindings(new Binding("test-name", Paths.get("test-path"),
	// @formatter:off
            Map.of(
                    Binding.TYPE, WeaviateBindingsPropertiesProcessor.TYPE,
                    "uri", "https://example.net:8000",
                    "api-key", "demo"
            )));
    // @formatter:on

	private final MockEnvironment environment = new MockEnvironment();

	private final Map<String, Object> properties = new HashMap<>();

	@Test
	void propertiesAreContributed() {
		new WeaviateBindingsPropertiesProcessor().process(environment, bindings, properties);
		assertThat(properties).containsEntry("spring.ai.vectorstore.weaviate.scheme", "https");
		assertThat(properties).containsEntry("spring.ai.vectorstore.weaviate.host", "example.net:8000");
		assertThat(properties).containsEntry("spring.ai.vectorstore.weaviate.api-key", "demo");
	}

	@Test
	void whenDisabledThenPropertiesAreNotContributed() {
		environment.setProperty("%s.weaviate.enabled".formatted(CONFIG_PATH), "false");

		new WeaviateBindingsPropertiesProcessor().process(environment, bindings, properties);
		assertThat(properties).isEmpty();
	}

}
