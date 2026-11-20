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

package org.springframework.ai.converter;

import java.util.List;

import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.core.ParameterizedTypeReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Xiangnan Zhang
 */
class BeanOutputConverterExceptionTests {

	@Test
	void retainsLongUnicodeTextBeforeCleaning() {
		// Exceed Jackson's source excerpt limit and include non-ASCII text.
		String text = "  <thinking>" + "分析 ".repeat(300) + "</thinking>\n```json\n{\"quantity\":\n```\n  ";
		var converter = new BeanOutputConverter<>(Order.class);

		assertThatExceptionOfType(StructuredOutputConversionException.class).isThrownBy(() -> converter.convert(text))
			.satisfies(ex -> {
				assertThat(ex.getText()).isEqualTo(text);
				assertThat(ex).hasCauseInstanceOf(JacksonException.class)
					.hasMessageContaining(Order.class.getTypeName());
				assertThat(ex.getMessage()).doesNotContain(text);
			});
	}

	@Test
	void retainsTextForParameterizedTypeMismatch() {
		var type = new ParameterizedTypeReference<List<Order>>() {
		};
		var converter = new BeanOutputConverter<>(type);
		String text = "[{\"quantity\":\"not a number\"}]";

		assertThatExceptionOfType(StructuredOutputConversionException.class).isThrownBy(() -> converter.convert(text))
			.satisfies(ex -> {
				assertThat(ex.getText()).isEqualTo(text);
				assertThat(ex).hasCauseInstanceOf(JacksonException.class)
					.hasMessageContaining(type.getType().getTypeName());
			});
	}

	@Test
	void preservesOriginalMapperExceptionAndCustomCleanerInput() {
		JsonMapper mapper = mock(JsonMapper.class);
		var targetType = JsonMapper.shared().constructType(Order.class);
		when(mapper.constructType(Order.class)).thenReturn(targetType);
		JacksonException cause = mock(JacksonException.class);
		when(mapper.readValue("cleaned", targetType)).thenThrow(cause);
		var converter = new BeanOutputConverter<>(Order.class, mapper, text -> "cleaned");

		assertThatExceptionOfType(StructuredOutputConversionException.class)
			.isThrownBy(() -> converter.convert("original"))
			.satisfies(ex -> {
				assertThat(ex.getText()).isEqualTo("original");
				assertThat(ex).hasCause(cause);
			});
	}

	@Test
	void doesNotWrapCustomCleanerFailures() {
		IllegalStateException failure = new IllegalStateException("Cleaner failed");
		var converter = new BeanOutputConverter<>(Order.class, null, text -> {
			throw failure;
		});

		assertThatThrownBy(() -> converter.convert("original")).isSameAs(failure);
	}

	record Order(int quantity) {
	}

}
