/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.mcp.annotation.context;

import java.util.Map;

import io.modelcontextprotocol.spec.McpSchema.LoggingLevel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link DefaultLoggingSpec}.
 *
 * @author Christian Tzolov
 */
public class DefaultLoggingSpecTests {

	@Test
	public void testMessageSetting() {
		DefaultLoggingSpec spec = new DefaultLoggingSpec();

		spec.message("Test log message");

		assertThat(spec.message).isEqualTo("Test log message");
	}

	@Test
	public void testLoggerSetting() {
		DefaultLoggingSpec spec = new DefaultLoggingSpec();

		spec.logger("test-logger");

		assertThat(spec.logger).isEqualTo("test-logger");
	}

	@Test
	public void testLevelSetting() {
		DefaultLoggingSpec spec = new DefaultLoggingSpec();

		spec.level(LoggingLevel.ERROR);

		assertThat(spec.level).isEqualTo(LoggingLevel.ERROR);
	}

	@Test
	public void testDefaultLevel() {
		DefaultLoggingSpec spec = new DefaultLoggingSpec();

		assertThat(spec.level).isEqualTo(LoggingLevel.INFO);
	}

	@Test
	public void testMetaWithMap() {
		DefaultLoggingSpec spec = new DefaultLoggingSpec();
		Map<String, Object> metaMap = Map.of("key1", "value1", "key2", "value2");

		spec.meta(metaMap);

		assertThat(spec.meta).containsEntry("key1", "value1").containsEntry("key2", "value2");
	}

	@Test
	public void testMetaWithNullMap() {
		DefaultLoggingSpec spec = new DefaultLoggingSpec();

		spec.meta((Map<String, Object>) null);

		assertThat(spec.meta).isEmpty();
	}

	@Test
	public void testMetaWithKeyValue() {
		DefaultLoggingSpec spec = new DefaultLoggingSpec();

		spec.meta("key", "value");

		assertThat(spec.meta).containsEntry("key", "value");
	}

	@Test
	public void testMetaWithNullKey() {
		DefaultLoggingSpec spec = new DefaultLoggingSpec();

		spec.meta(null, "value");

		assertThat(spec.meta).isEmpty();
	}

	@Test
	public void testMetaWithNullValue() {
		DefaultLoggingSpec spec = new DefaultLoggingSpec();

		spec.meta("key", null);

		assertThat(spec.meta).isEmpty();
	}

	@Test
	public void testMetaMultipleEntries() {
		DefaultLoggingSpec spec = new DefaultLoggingSpec();

		spec.meta("key1", "value1").meta("key2", "value2").meta("key3", "value3");

		assertThat(spec.meta).hasSize(3)
			.containsEntry("key1", "value1")
			.containsEntry("key2", "value2")
			.containsEntry("key3", "value3");
	}

	@Test
	public void testFluentInterface() {
		DefaultLoggingSpec spec = new DefaultLoggingSpec();

		McpRequestContextTypes.LoggingSpec result = spec.message("Test message")
			.logger("test-logger")
			.level(LoggingLevel.DEBUG)
			.meta("key", "value");

		assertThat(result).isSameAs(spec);
		assertThat(spec.message).isEqualTo("Test message");
		assertThat(spec.logger).isEqualTo("test-logger");
		assertThat(spec.level).isEqualTo(LoggingLevel.DEBUG);
		assertThat(spec.meta).containsEntry("key", "value");
	}

	@Test
	public void testAllLoggingLevels() {
		DefaultLoggingSpec spec = new DefaultLoggingSpec();

		spec.level(LoggingLevel.DEBUG);
		assertThat(spec.level).isEqualTo(LoggingLevel.DEBUG);

		spec.level(LoggingLevel.INFO);
		assertThat(spec.level).isEqualTo(LoggingLevel.INFO);

		spec.level(LoggingLevel.WARNING);
		assertThat(spec.level).isEqualTo(LoggingLevel.WARNING);

		spec.level(LoggingLevel.ERROR);
		assertThat(spec.level).isEqualTo(LoggingLevel.ERROR);
	}

}
