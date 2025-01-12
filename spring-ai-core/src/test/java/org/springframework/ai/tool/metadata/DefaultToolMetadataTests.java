package org.springframework.ai.tool.metadata;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.execution.ToolExecutionMode;

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
		assertThat(toolMetadata.executionMode()).isEqualTo(ToolExecutionMode.BLOCKING);
		assertThat(toolMetadata.returnDirect()).isFalse();
	}

	@Test
	void shouldCreateDefaultToolMetadataWithGivenValues() {
		var toolMetadata = DefaultToolMetadata.builder()
			.executionMode(ToolExecutionMode.BLOCKING)
			.returnDirect(true)
			.build();
		assertThat(toolMetadata.executionMode()).isEqualTo(ToolExecutionMode.BLOCKING);
		assertThat(toolMetadata.returnDirect()).isTrue();
	}

}
