/*
 * Copyright 2023 - 2024 the original author or authors.
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
package org.springframework.ai.autoconfigure.vectorstore.redis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * @author Julien Ruaux
 */
class RedisVectorStorePropertiesTests {

	@Test
	void defaultValues() {
		var props = new RedisVectorStoreProperties();
		assertThat(props.getUri()).isEqualTo("redis://localhost:6379");
		assertThat(props.getIndex()).isEqualTo("default-index");
		assertThat(props.getPrefix()).isEqualTo("default:");
	}

	@Test
	void customValues() {
		var props = new RedisVectorStoreProperties();
		props.setUri("redis://redis.com:12345");
		props.setIndex("myIdx");
		props.setPrefix("doc:");

		assertThat(props.getUri()).isEqualTo("redis://redis.com:12345");
		assertThat(props.getIndex()).isEqualTo("myIdx");
		assertThat(props.getPrefix()).isEqualTo("doc:");
	}

}
