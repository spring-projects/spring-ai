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

package org.springframework.ai.converter;

import org.springframework.core.convert.support.DefaultConversionService;

/**
 * Abstract {@link StructuredOutputConverter} implementation that uses a pre-configured
 * {@link DefaultConversionService} to convert the LLM output into the desired type
 * format.
 *
 * @param <T> Specifies the desired response type.
 * @author Mark Pollack
 * @author Christian Tzolov
 */
public abstract class AbstractConversionServiceOutputConverter<T> implements StructuredOutputConverter<T> {

	private final DefaultConversionService conversionService;

	/**
	 * Create a new {@link AbstractConversionServiceOutputConverter} instance.
	 * @param conversionService the {@link DefaultConversionService} to use for converting
	 * the output.
	 */
	public AbstractConversionServiceOutputConverter(DefaultConversionService conversionService) {
		this.conversionService = conversionService;
	}

	/**
	 * Return the ConversionService used by this converter.
	 * @return the ConversionService used by this converter.
	 */
	public DefaultConversionService getConversionService() {
		return this.conversionService;
	}

}
