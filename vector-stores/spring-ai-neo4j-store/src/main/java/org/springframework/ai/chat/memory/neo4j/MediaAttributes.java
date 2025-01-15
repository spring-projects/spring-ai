package org.springframework.ai.chat.memory.neo4j;

import org.springframework.ai.model.Media;

/**
 * Keys to get/set values for {@link Media} object maps
 *
 * @author Enrico Rampazzo
 */

public enum MediaAttributes {
	ID("id"), MIME_TYPE("mimeType"), DATA("data"), NAME("name"), URL("url"),
	IDX("idx");

	private final String value;

	MediaAttributes(String value){
		this.value = value;
	}

	public String getValue(){
		return value;
	}
}
