package org.springframework.ai.chat.memory.neo4j;

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
