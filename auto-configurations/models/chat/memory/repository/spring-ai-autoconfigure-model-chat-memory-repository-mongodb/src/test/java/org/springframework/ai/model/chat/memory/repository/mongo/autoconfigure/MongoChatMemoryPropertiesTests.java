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

package org.springframework.ai.model.chat.memory.repository.mongo.autoconfigure;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MongoChatMemoryPropertiesTests {

	@Test
	void defaultValues_set() {
		var properties = new MongoChatMemoryProperties();
		assertThat(properties.getTtl()).isEqualTo(Duration.ZERO);
		assertThat(properties.isCreateIndices()).isFalse();
	}

	@Test
	void overrideValues() {
		var properties = new MongoChatMemoryProperties();
		properties.setTtl(Duration.ofMinutes(1));
		properties.setCreateIndices(true);

		assertThat(properties.getTtl()).isEqualTo(Duration.ofMinutes(1));
		assertThat(properties.isCreateIndices()).isTrue();
	}

}
