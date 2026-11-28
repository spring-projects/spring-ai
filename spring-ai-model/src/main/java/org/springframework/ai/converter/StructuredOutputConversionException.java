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

import org.springframework.util.Assert;

/**
 * Thrown when model output cannot be converted to the requested structured type. Provides
 * the complete input text before response cleaning, with the conversion failure as the
 * {@linkplain #getCause() cause}.
 *
 * @author Xiangnan Zhang
 * @since 2.1.1
 * @see BeanOutputConverter
 */
public class StructuredOutputConversionException extends RuntimeException {

	private final String text;

	/**
	 * Create an exception retaining the original model output.
	 * @param message a description of the conversion failure
	 * @param text the complete input text before response cleaning
	 * @param cause the underlying conversion failure
	 * @since 2.1.1
	 */
	public StructuredOutputConversionException(String message, String text, Throwable cause) {
		super(message, cause);
		Assert.notNull(text, "text cannot be null");
		this.text = text;
	}

	/**
	 * Return the complete input text, including any whitespace, thinking tags or Markdown
	 * code fences removed during response cleaning.
	 * @return the original, untruncated input text
	 * @since 2.1.1
	 */
	public String getText() {
		return this.text;
	}

}
