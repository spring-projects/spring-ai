package org.springframework.ai.document.id.impl;

import org.springframework.ai.document.id.IdGenerator;

import java.util.Map;
import java.util.UUID;

public class RandomIdGenerator implements IdGenerator {

	@Override
	public String generateIdFrom(String content, Map<String, Object> metadata) {
		return UUID.randomUUID().toString();
	}

}
