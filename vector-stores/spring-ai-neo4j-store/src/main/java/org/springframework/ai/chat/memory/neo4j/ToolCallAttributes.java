package org.springframework.ai.chat.memory.neo4j;

import org.springframework.ai.chat.messages.AssistantMessage;

/**
 * Keys to get/set values for {@link AssistantMessage.ToolCall} object maps
 *
 * @author Enrico Rampazzo
 */

public enum ToolCallAttributes {
	 ID("id"), NAME("name"), ARGUMENTS("arguments"), TYPE("type"), IDX("idx");

	private final String value;

	ToolCallAttributes(String value){
		 this.value = value;
	}

	public String getValue() {
		return value;
	}
}
