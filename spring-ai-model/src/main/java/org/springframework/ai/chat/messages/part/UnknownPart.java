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

package org.springframework.ai.chat.messages.part;

import java.util.Map;

import org.jspecify.annotations.Nullable;
import tools.jackson.core.JsonParser;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.deser.std.StdDeserializer;

import org.springframework.util.Assert;

/**
 * A part that neither an adapter nor this version of Spring AI recognizes, kept verbatim
 * so nothing is silently dropped.
 * <p>
 * Two things produce an unknown part: a provider adapter that meets a response block it
 * does not model (a citation, a server tool result, a future block type), and
 * deserialization of a part whose {@code type} was written by a newer Spring AI version.
 * In the second case {@code provider} is {@link #SPRING_AI_PROVIDER}, so no adapter
 * mistakes it for one of its own blocks. An unknown part is only ever replayed to the
 * provider named in {@code provider}.
 *
 * @param provider who produced the part
 * @param kind the producer's own discriminator, for example {@code server_tool_use}
 * @param rawJson the part exactly as received
 * @param payload provider data to replay with this part, or {@code null}
 * @param attributes free-form attributes, copied and unmodifiable
 * @author Christian Tzolov
 * @since 2.1.0
 */
@JsonDeserialize(using = UnknownPart.Deserializer.class)
public record UnknownPart(String provider, String kind, String rawJson, @Nullable OpaquePayload payload,
		Map<String, String> attributes) implements MessagePart {

	/**
	 * The {@code provider} of an unknown part produced by deserializing a {@code type}
	 * this version does not know.
	 */
	public static final String SPRING_AI_PROVIDER = "spring-ai";

	public UnknownPart {
		Assert.hasText(provider, "provider must not be empty");
		Assert.hasText(kind, "kind must not be empty");
		Assert.notNull(rawJson, "rawJson must not be null");
		Assert.notNull(attributes, "attributes must not be null");
		attributes = Map.copyOf(attributes);
	}

	@Override
	public UnknownPart withAttributes(Map<String, String> attributes) {
		return new UnknownPart(this.provider, this.kind, this.rawJson, this.payload, attributes);
	}

	/**
	 * Reads an {@link UnknownPart} from its own serialized form (recognized by the
	 * {@code rawJson} property), or wraps a whole object of a foreign {@code type}.
	 */
	static final class Deserializer extends StdDeserializer<UnknownPart> {

		private static final TypeReference<Map<String, String>> ATTRIBUTES = new TypeReference<>() {
		};

		Deserializer() {
			super(UnknownPart.class);
		}

		@Override
		public UnknownPart deserialize(JsonParser parser, DeserializationContext context) {
			JsonNode node = parser.readValueAsTree();
			if (node.has("rawJson")) {
				OpaquePayload payload = node.hasNonNull("payload")
						? context.readTreeAsValue(node.get("payload"), OpaquePayload.class) : null;
				Map<String, String> attributes = node.hasNonNull("attributes")
						? context.readTreeAsValue(node.get("attributes"), context.constructType(ATTRIBUTES.getType()))
						: Map.of();
				return new UnknownPart(node.path("provider").asString(SPRING_AI_PROVIDER),
						node.path("kind").asString("unknown"), node.path("rawJson").asString(""), payload, attributes);
			}
			return new UnknownPart(SPRING_AI_PROVIDER, node.path("type").asString("unknown"), node.toString(), null,
					Map.of());
		}

	}

}
