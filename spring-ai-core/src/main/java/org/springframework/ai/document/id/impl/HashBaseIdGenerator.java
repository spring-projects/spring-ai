package org.springframework.ai.document.id.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.document.id.IdGenerator;

import java.util.Map;

abstract class HashBaseIdGenerator implements IdGenerator {

	private final ObjectMapper objectMapper = getObjectMapper();

	private final String idTemplate = "{\"content\": \"%s\", \"metadata\": \"%s\"}";

	@Override
	public String generateIdFrom(String content, Map<String, Object> metadata) {
		final String metadataString = serialize(metadata);
		final String contentWithMetadata = String.format(idTemplate, content, metadataString);
		return hash(contentWithMetadata); // To be implemented by subclasses
	}

	abstract String hash(final String s);

	private String serialize(final Map<String, Object> metadata) {
		try {
			return objectMapper.writeValueAsString(metadata);
		}
		catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}

	private ObjectMapper getObjectMapper() {
		return new ObjectMapper();
	}

}
