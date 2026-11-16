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

package org.springframework.ai.systemone.api.response;

import java.util.LinkedHashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;

/**
 * Dispatches an answer object onto the right {@link Answer} record based on its
 * {@code type} discriminator, falling back to {@link UnknownAnswer} for a kind this
 * client version does not model.
 *
 * <p>
 * This is registered on the {@code answers} map of {@link SystemOneResponse} rather than
 * on the {@link Answer} interface itself, so that reading a concrete record still uses
 * Jackson's ordinary record binding and cannot recurse back into this deserializer.
 *
 * @author Christian Tzolov
 */
class AnswerDeserializer extends ValueDeserializer<Answer> {

	@Override
	public Answer deserialize(JsonParser parser, DeserializationContext context) {
		JsonNode node = context.readTree(parser);
		JsonNode typeNode = node.get("type");
		// Jackson 3's stringValue() is strict and throws on a non-string node. A type
		// that is a number or an object is still a kind this client does not model, so
		// it must reach toUnknown rather than abort the whole response.
		String typeName = (typeNode != null && typeNode.isString()) ? typeNode.stringValue() : null;

		if (typeName == null) {
			return toUnknown(null, node, context);
		}
		return switch (typeName) {
			case "noul" -> context.readTreeAsValue(node, NoulAnswer.class);
			case "choice" -> context.readTreeAsValue(node, ChoiceAnswer.class);
			case "score" -> context.readTreeAsValue(node, ScoreAnswer.class);
			default -> toUnknown(typeName, node, context);
		};
	}

	/**
	 * A JSON {@code null} where an answer object was expected. Jackson does not call
	 * {@link #deserialize} for it, so without this the answer would be a {@code null} map
	 * entry that passes every guard and fails far from the response.
	 */
	@Override
	public UnknownAnswer getNullValue(DeserializationContext context) {
		return new UnknownAnswer(null, Map.of());
	}

	private UnknownAnswer toUnknown(@Nullable String typeName, JsonNode node, DeserializationContext context) {
		Map<String, Object> raw = context.readTreeAsValue(node,
				context.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class));
		return new UnknownAnswer(typeName, raw);
	}

}
