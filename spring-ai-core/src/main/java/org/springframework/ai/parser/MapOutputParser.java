package org.springframework.ai.parser;

import org.springframework.messaging.Message;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.support.MessageBuilder;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Uses Jackson
 */
public class MapOutputParser extends AbstractMessageConverterOutputParser<Map<String, Object>> {

	public MapOutputParser() {
		super(new MappingJackson2MessageConverter());
	}

	@Override
	public Map<String, Object> parse(String text) {
		Message<?> message = MessageBuilder.withPayload(text.getBytes(StandardCharsets.UTF_8)).build();
		return (Map) getMessageConverter().fromMessage(message, HashMap.class);
	}

	@Override
	public String getFormat() {
		String raw = """
				Your response should be in JSON format.
				The data structure for the JSON should match this Java class: %s
				Do not include any explanations, only provide a RFC8259 compliant JSON response following this format without deviation.
				 """;
		return String.format(raw, "java.util.HashMap");
	}

}
