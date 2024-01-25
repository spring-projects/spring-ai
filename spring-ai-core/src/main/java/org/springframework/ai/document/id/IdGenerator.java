package org.springframework.ai.document.id;

import java.util.Map;

public interface IdGenerator {

	String generateIdFrom(String content, Map<String, Object> metadata);

}
