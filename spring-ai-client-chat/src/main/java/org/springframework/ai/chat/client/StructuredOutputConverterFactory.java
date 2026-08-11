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

package org.springframework.ai.chat.client;

import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.converter.ResponseTextCleaner;
import org.springframework.ai.converter.StructuredOutputConverter;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.util.Assert;

/**
 * Factory for creating {@link StructuredOutputConverter} instances for type-based
 * {@link ChatClient.CallResponseSpec#entity(Class)} and
 * {@link ChatClient.CallResponseSpec#responseEntity(Class)} calls.
 * <p>
 * Implementations must return a non-null converter that supports the supplied target
 * type. A factory is shared by the {@link ChatClient} instances created or derived from a
 * configured builder and must therefore be safe to invoke concurrently. Any converter
 * instance shared between invocations must also be safe for concurrent use.
 * <p>
 * Explicit converter overloads bypass this factory.
 *
 * @author Jaehyeon Park
 * @since 2.0.1
 */
public interface StructuredOutputConverterFactory {

	/**
	 * Create a converter for the given target type.
	 * @param targetType the target type
	 * @return a non-null structured output converter
	 * @since 2.0.1
	 */
	<T> StructuredOutputConverter<T> create(ParameterizedTypeReference<T> targetType);

	/**
	 * Create a factory that uses {@link BeanOutputConverter} with the given JSON mapper.
	 * @param jsonMapper the JSON mapper
	 * @return a structured output converter factory
	 * @since 2.0.1
	 */
	static StructuredOutputConverterFactory beanOutputConverter(JsonMapper jsonMapper) {
		Assert.notNull(jsonMapper, "jsonMapper cannot be null");
		return new StructuredOutputConverterFactory() {
			@Override
			public <T> StructuredOutputConverter<T> create(ParameterizedTypeReference<T> targetType) {
				return new BeanOutputConverter<>(targetType, jsonMapper);
			}
		};
	}

	/**
	 * Create a factory that uses {@link BeanOutputConverter} with the given JSON mapper
	 * and response text cleaner.
	 * @param jsonMapper the JSON mapper
	 * @param textCleaner the response text cleaner
	 * @return a structured output converter factory
	 * @since 2.0.1
	 */
	static StructuredOutputConverterFactory beanOutputConverter(JsonMapper jsonMapper,
			ResponseTextCleaner textCleaner) {
		Assert.notNull(jsonMapper, "jsonMapper cannot be null");
		Assert.notNull(textCleaner, "textCleaner cannot be null");
		return new StructuredOutputConverterFactory() {
			@Override
			public <T> StructuredOutputConverter<T> create(ParameterizedTypeReference<T> targetType) {
				return new BeanOutputConverter<>(targetType, jsonMapper, textCleaner);
			}
		};
	}

}
