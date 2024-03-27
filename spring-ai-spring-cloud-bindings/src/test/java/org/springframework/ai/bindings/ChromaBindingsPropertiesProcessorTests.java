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
 * Unit tests for {@link ChromaBindingsPropertiesProcessor}.
 *
 * @author Thomas Vitale
 */
class ChromaBindingsPropertiesProcessorTests {

	private final Bindings bindings = new Bindings(new Binding("test-name", Paths.get("test-path"),
	// @formatter:off
            Map.of(
				Binding.TYPE, ChromaBindingsPropertiesProcessor.TYPE,
				"uri", "https://example.net:8000",
				"username", "itsme",
				"password", "youknowit"
            )));
	// @formatter:on

	private final MockEnvironment environment = new MockEnvironment();

	private final Map<String, Object> properties = new HashMap<>();

	@Test
	void propertiesAreContributed() {
		new ChromaBindingsPropertiesProcessor().process(environment, bindings, properties);
		assertThat(properties).containsEntry("spring.ai.vectorstore.chroma.client.host", "https://example.net");
		assertThat(properties).containsEntry("spring.ai.vectorstore.chroma.client.port", "8000");
		assertThat(properties).containsEntry("spring.ai.vectorstore.chroma.client.username", "itsme");
		assertThat(properties).containsEntry("spring.ai.vectorstore.chroma.client.password", "youknowit");
	}

	@Test
	void whenDisabledThenPropertiesAreNotContributed() {
		environment.setProperty("%s.chroma.enabled".formatted(CONFIG_PATH), "false");

		new ChromaBindingsPropertiesProcessor().process(environment, bindings, properties);
		assertThat(properties).isEmpty();
	}

}
