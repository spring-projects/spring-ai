package org.springframework.ai.chat.memory.neo4j;

import org.springframework.ai.chat.messages.Message;

/**
 * Keys to get/set values for {@link Message} object maps
 *
 * @author Enrico Rampazzo
 */

public enum MessageAttributes {
	TEXT_CONTENT("textContent"), MESSAGE_TYPE("messageType");

	private final String value;

	public String getValue(){
		return value;
	}
	MessageAttributes(String value) {
		this.value = value;
	}
}
