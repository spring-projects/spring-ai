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

import org.springframework.messaging.converter.MessageConverter;

/**
 * Abstract {@link StructuredOutputConverter} implementation that uses a pre-configured
 * {@link MessageConverter} to convert the LLM output into the desired type format.
 *
 * @param <T> Specifies the desired response type.
 * @author Mark Pollack
 * @author Christian Tzolov
 */
public abstract class AbstractMessageOutputConverter<T> implements StructuredOutputConverter<T> {

	private MessageConverter messageConverter;

	/**
	 * Create a new AbstractMessageOutputConverter.
	 * @param messageConverter the message converter to use
	 */
	public AbstractMessageOutputConverter(MessageConverter messageConverter) {
		this.messageConverter = messageConverter;
	}

	/**
	 * Return the message converter used by this output converter.
	 * @return the message converter
	 */
	public MessageConverter getMessageConverter() {
		return this.messageConverter;
	}

}
