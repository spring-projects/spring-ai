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

package org.springframework.ai.model;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link EmbeddingUtils}.
 *
 * @author Abu Hena Mostafa Kamal
 */
class EmbeddingUtilsTests {

	@Test
	void doubleToFloatShouldConvertCorrectly() {
		List<Float> result = EmbeddingUtils.doubleToFloat(List.of(1.0, 2.0, 3.0));
		assertThat(result).containsExactly(1.0f, 2.0f, 3.0f);
	}

	@Test
	void doubleToFloatShouldReturnEmptyListForEmptyInput() {
		assertThat(EmbeddingUtils.doubleToFloat(List.of())).isEmpty();
	}

	@Test
	void toPrimitiveListShouldConvertCorrectly() {
		float[] result = EmbeddingUtils.toPrimitive(List.of(1.0f, 2.0f, 3.0f));
		assertThat(result).containsExactly(1.0f, 2.0f, 3.0f);
	}

	@Test
	void toPrimitiveListShouldReturnEmptyArrayForNullInput() {
		assertThat(EmbeddingUtils.toPrimitive((List<Float>) null)).isEmpty();
	}

	@Test
	void toPrimitiveListShouldReturnEmptyArrayForEmptyInput() {
		assertThat(EmbeddingUtils.toPrimitive(List.of())).isEmpty();
	}

	@Test
	void toPrimitiveArrayShouldConvertCorrectly() {
		float[] result = EmbeddingUtils.toPrimitive(new Float[] { 1.0f, 2.0f, 3.0f });
		assertThat(result).containsExactly(1.0f, 2.0f, 3.0f);
	}

	@Test
	void toPrimitiveArrayShouldReturnEmptyArrayForNullInput() {
		assertThat(EmbeddingUtils.toPrimitive((Float[]) null)).isEmpty();
	}

	@Test
	void toPrimitiveArrayShouldReturnEmptyArrayForEmptyInput() {
		assertThat(EmbeddingUtils.toPrimitive(new Float[0])).isEmpty();
	}

	@Test
	void toFloatArrayShouldConvertCorrectly() {
		Float[] result = EmbeddingUtils.toFloatArray(new float[] { 1.0f, 2.0f, 3.0f });
		assertThat(result).containsExactly(1.0f, 2.0f, 3.0f);
	}

	@Test
	void toFloatArrayShouldReturnEmptyArrayForNullInput() {
		assertThat(EmbeddingUtils.toFloatArray(null)).isEmpty();
	}

	@Test
	void toFloatArrayShouldReturnEmptyArrayForEmptyInput() {
		assertThat(EmbeddingUtils.toFloatArray(new float[0])).isEmpty();
	}

	@Test
	void toListShouldConvertCorrectly() {
		List<Float> result = EmbeddingUtils.toList(new float[] { 1.0f, 2.0f, 3.0f });
		assertThat(result).containsExactly(1.0f, 2.0f, 3.0f);
	}

	@Test
	void toListShouldReturnEmptyListForEmptyInput() {
		assertThat(EmbeddingUtils.toList(new float[0])).isEmpty();
	}

}
