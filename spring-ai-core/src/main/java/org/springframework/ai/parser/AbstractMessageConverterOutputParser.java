package org.springframework.ai.parser;

import org.springframework.messaging.converter.MessageConverter;

public abstract class AbstractMessageConverterOutputParser<T> implements OutputParser<T> {

	private MessageConverter messageConverter;

	public AbstractMessageConverterOutputParser(MessageConverter messageConverter) {
		this.messageConverter = messageConverter;
	}

	public MessageConverter getMessageConverter() {
		return messageConverter;
	}

}
