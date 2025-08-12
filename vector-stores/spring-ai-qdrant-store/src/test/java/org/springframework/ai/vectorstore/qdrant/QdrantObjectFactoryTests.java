/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.vectorstore.qdrant;

import java.util.Map;

import io.qdrant.client.grpc.JsonWithInt.NullValue;
import io.qdrant.client.grpc.JsonWithInt.Value;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link QdrantObjectFactory}.
 *
 * ignore: test 4 for github workflow trigger on commit.
 *
 * @author Heonwoo Kim
 */

class QdrantObjectFactoryTests {

	@Test
	void toObjectMapShouldHandleNullValues() {
		Map<String, Value> payloadWithNull = Map.of("name", Value.newBuilder().setStringValue("Spring AI").build(),
				"version", Value.newBuilder().setDoubleValue(1.0).build(), "is_ga",
				Value.newBuilder().setBoolValue(true).build(), "description",
				Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build());

		Map<String, Object> result = QdrantObjectFactory.toObjectMap(payloadWithNull);

		assertThat(result).isNotNull();
		assertThat(result).hasSize(4);
		assertThat(result.get("name")).isEqualTo("Spring AI");
		assertThat(result.get("version")).isEqualTo(1.0);
		assertThat(result.get("is_ga")).isEqualTo(true);
		assertThat(result).containsKey("description");
		assertThat(result.get("description")).isNull();
	}

}
