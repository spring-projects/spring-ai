package org.springframework.ai.chat.memory.neo4j;

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
