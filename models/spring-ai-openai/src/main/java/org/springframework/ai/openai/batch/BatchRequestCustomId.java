/*
 * Copyright 2023-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.openai.batch;

import org.springframework.util.Assert;

/**
 * Represents a custom ID for an OpenAI Batch API request line. Uses a {@code ::}
 * delimiter to safely encode an entity identifier and a handler identifier.
 * <p>
 * The {@code ::} delimiter is chosen over single characters (e.g., {@code _} or
 * {@code -}) to avoid collisions with identifiers that may naturally contain those
 * characters.
 *
 * @author Yasin Akbas
 * @since 2.0.0
 */
public record BatchRequestCustomId(String entityId, String handlerId) {

	/**
	 * Delimiter used to separate the entity ID and handler ID in the serialized custom ID
	 * string.
	 */
	public static final String DELIMITER = "::";

	public BatchRequestCustomId {
		Assert.hasText(entityId, "entityId must not be blank");
		Assert.hasText(handlerId, "handlerId must not be blank");
		Assert.isTrue(!entityId.contains(DELIMITER), "entityId must not contain the delimiter '" + DELIMITER + "'");
		Assert.isTrue(!handlerId.contains(DELIMITER), "handlerId must not contain the delimiter '" + DELIMITER + "'");
	}

	/**
	 * Parses a custom ID string into a {@link BatchRequestCustomId}.
	 * @param customId the custom ID string in the format {@code entityId::handlerId}
	 * @return the parsed custom ID
	 * @throws IllegalArgumentException if the string does not contain exactly two parts
	 */
	public static BatchRequestCustomId parse(String customId) {
		Assert.hasText(customId, "customId must not be blank");
		String[] parts = customId.split(DELIMITER, -1);
		if (parts.length != 2) {
			throw new IllegalArgumentException(
					"Invalid custom ID format: expected 'entityId" + DELIMITER + "handlerId', got: " + customId);
		}
		return new BatchRequestCustomId(parts[0], parts[1]);
	}

	@Override
	public String toString() {
		return this.entityId + DELIMITER + this.handlerId;
	}

}
