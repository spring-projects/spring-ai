package org.springframework.ai.parser;

import org.springframework.messaging.Message;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.support.MessageBuilder;

import java.nio.charset.StandardCharsets;

/**
 * Uses Jackson
 */
public class JsonOutputParser extends AbstractMessageConverterOutputParser {

	private Class dataType;

	public JsonOutputParser(Class dataType) {
		super(new MappingJackson2MessageConverter());
		this.dataType = dataType;
	}

	@Override
	public Object parse(String text) {
		Message<?> message = MessageBuilder.withPayload(text.getBytes(StandardCharsets.UTF_8)).build();
		return getMessageConverter().fromMessage(message, dataType);
	}

	@Override
	public String getFormat() {
		String raw = """
				Your response should be in JSON format.
				The data structure for the JSON should match this Java class: %s
				Do not include any explanations, only provide a RFC8259 compliant JSON response following this format without deviation.
				 """;
		return String.format(raw, dataType.getCanonicalName());
	}

}
