package org.springframework.ai.chat.memory.neo4j;

public enum ToolResponseAttributes {

	IDX("idx"), RESPONSE_DATA("responseData"), NAME("name"), ID("id");

	private final String value;

	ToolResponseAttributes(String value) {
		this.value = value;
	}

	public String getValue() {
		return value;
	}

}
