/*
 * Copyright 2023-present the original author or authors.
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

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link DefaultProgressSpec}.
 *
 * @author Christian Tzolov
 */
public class DefaultProgressSpecTests {

	@Test
	public void testDefaultValues() {
		DefaultProgressSpec spec = new DefaultProgressSpec();

		assertThat(spec.progress).isEqualTo(0.0);
		assertThat(spec.total).isEqualTo(1.0);
		assertThat(spec.message).isNull();
		assertThat(spec.meta).isEmpty();
	}

	@Test
	public void testProgressSetting() {
		DefaultProgressSpec spec = new DefaultProgressSpec();

		spec.progress(0.5);

		assertThat(spec.progress).isEqualTo(0.5);
	}

	@Test
	public void testTotalSetting() {
		DefaultProgressSpec spec = new DefaultProgressSpec();

		spec.total(100.0);

		assertThat(spec.total).isEqualTo(100.0);
	}

	@Test
	public void testMessageSetting() {
		DefaultProgressSpec spec = new DefaultProgressSpec();

		spec.message("Processing...");

		assertThat(spec.message).isEqualTo("Processing...");
	}

	@Test
	public void testMetaWithMap() {
		DefaultProgressSpec spec = new DefaultProgressSpec();
		Map<String, Object> metaMap = new HashMap<>();
		metaMap.put("key1", "value1");
		metaMap.put("key2", "value2");

		spec.meta(metaMap);

		assertThat(spec.meta).containsEntry("key1", "value1").containsEntry("key2", "value2");
	}

	@Test
	public void testMetaWithNullMap() {
		DefaultProgressSpec spec = new DefaultProgressSpec();

		spec.meta((Map<String, Object>) null);

		assertThat(spec.meta).isEmpty();
	}

	@Test
	public void testMetaWithKeyValue() {
		DefaultProgressSpec spec = new DefaultProgressSpec();
		spec.meta = new HashMap<>();

		spec.meta("key", "value");

		assertThat(spec.meta).containsEntry("key", "value");
	}

	@Test
	public void testMetaWithNullKey() {
		DefaultProgressSpec spec = new DefaultProgressSpec();
		spec.meta = new HashMap<>();

		spec.meta(null, "value");

		assertThat(spec.meta).isEmpty();
	}

	@Test
	public void testMetaWithNullValue() {
		DefaultProgressSpec spec = new DefaultProgressSpec();
		spec.meta = new HashMap<>();

		spec.meta("key", null);

		assertThat(spec.meta).isEmpty();
	}

	@Test
	public void testMetaMultipleEntries() {
		DefaultProgressSpec spec = new DefaultProgressSpec();
		spec.meta = new HashMap<>();

		spec.meta("key1", "value1").meta("key2", "value2").meta("key3", "value3");

		assertThat(spec.meta).hasSize(3)
			.containsEntry("key1", "value1")
			.containsEntry("key2", "value2")
			.containsEntry("key3", "value3");
	}

	@Test
	public void testFluentInterface() {
		DefaultProgressSpec spec = new DefaultProgressSpec();
		spec.meta = new HashMap<>();

		McpRequestContextTypes.ProgressSpec result = spec.progress(0.75)
			.total(1.0)
			.message("Processing...")
			.meta("key", "value");

		assertThat(result).isSameAs(spec);
		assertThat(spec.progress).isEqualTo(0.75);
		assertThat(spec.total).isEqualTo(1.0);
		assertThat(spec.message).isEqualTo("Processing...");
		assertThat(spec.meta).containsEntry("key", "value");
	}

	@Test
	public void testProgressBoundaries() {
		DefaultProgressSpec spec = new DefaultProgressSpec();

		spec.progress(0.0);
		assertThat(spec.progress).isEqualTo(0.0);

		spec.progress(1.0);
		assertThat(spec.progress).isEqualTo(1.0);

		spec.progress(0.5);
		assertThat(spec.progress).isEqualTo(0.5);
	}

	@Test
	public void testTotalValues() {
		DefaultProgressSpec spec = new DefaultProgressSpec();

		spec.total(50.0);
		assertThat(spec.total).isEqualTo(50.0);

		spec.total(100.0);
		assertThat(spec.total).isEqualTo(100.0);

		spec.total(1.0);
		assertThat(spec.total).isEqualTo(1.0);
	}

}
