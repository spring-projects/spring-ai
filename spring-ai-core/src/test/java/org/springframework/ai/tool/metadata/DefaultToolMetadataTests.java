package org.springframework.ai.tool.metadata;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DefaultToolMetadata}.
 *
 * @author Thomas Vitale
 */
class DefaultToolMetadataTests {

	@Test
	void shouldCreateDefaultToolMetadataWithDefaultValues() {
		var toolMetadata = DefaultToolMetadata.builder().build();
		assertThat(toolMetadata.returnDirect()).isFalse();
	}

	@Test
	void shouldCreateDefaultToolMetadataWithGivenValues() {
		var toolMetadata = DefaultToolMetadata.builder().returnDirect(true).build();
		assertThat(toolMetadata.returnDirect()).isTrue();
	}

}
