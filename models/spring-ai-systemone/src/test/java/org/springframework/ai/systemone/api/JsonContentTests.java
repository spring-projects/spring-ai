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

package org.springframework.ai.systemone.api;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonValue;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * The {@code EntryType} union the API uses for {@code state}, {@code instructions} and
 * every criteria description has to round-trip as a bare JSON value, never as a wrapper
 * object.
 *
 * @author Christian Tzolov
 */
class JsonContentTests {

	private final JsonMapper mapper = JsonMapper.builder().build();

	@Test
	void serializesTextAsABareString() {
		assertThat(this.mapper.writeValueAsString(JsonContent.of("Calm"))).isEqualTo("\"Calm\"");
	}

	@Test
	void serializesAnObjectAsAJsonObject() {
		JsonContent content = JsonContent.of(Map.of("question", "Does this convey urgency?"));
		assertThat(this.mapper.writeValueAsString(content)).isEqualTo("{\"question\":\"Does this convey urgency?\"}");
	}

	@Test
	void buildsAnObjectThatKeepsTheOrderItsKeysAreWrittenIn() {
		JsonContent content = JsonContent.object("question", "Does the `message` ask for a password?", "inspect",
				"message", "focus", "A request to send it, not to reset it.");

		assertThat(this.mapper.writeValueAsString(content))
			.isEqualTo("{\"question\":\"Does the `message` ask for a password?\",\"inspect\":\"message\","
					+ "\"focus\":\"A request to send it, not to reset it.\"}");
		assertThat(content.asMap().keySet()).containsExactly("question", "inspect", "focus");
	}

	@Test
	void nestsAndAcceptsNullValuesInAnObject() {
		JsonContent content = JsonContent.object("ticket", JsonContent.object("id", 7, "sender", null), "tags",
				List.of("billing"));

		assertThat(this.mapper.writeValueAsString(content))
			.isEqualTo("{\"ticket\":{\"id\":7,\"sender\":null},\"tags\":[\"billing\"]}");
	}

	@Test
	void anObjectIsUnmodifiable() {
		assertThatExceptionOfType(UnsupportedOperationException.class)
			.isThrownBy(() -> JsonContent.object("a", 1).asMap().put("b", 2));
	}

	@Test
	void rejectsAnObjectThatIsNotKeyValuePairs() {
		assertThatIllegalArgumentException().isThrownBy(() -> JsonContent.object("question"))
			.withMessageContaining("odd number");
		assertThatIllegalArgumentException().isThrownBy(() -> JsonContent.object(1, "one"))
			.withMessageContaining("position 0 must be a non-empty String");
		assertThatIllegalArgumentException().isThrownBy(() -> JsonContent.object("a", 1, "", 2))
			.withMessageContaining("position 1");
		assertThatIllegalArgumentException().isThrownBy(() -> JsonContent.object("a", 1, "a", 2))
			.withMessageContaining("duplicate key 'a'");
	}

	@Test
	void serializesAnArrayAsAJsonArray() {
		JsonContent content = JsonContent.of(List.of("ticket.sender.display_name", "ticket.sender.email"));
		assertThat(this.mapper.writeValueAsString(content))
			.isEqualTo("[\"ticket.sender.display_name\",\"ticket.sender.email\"]");
	}

	@Test
	void serializesNullAsJsonNull() {
		assertThat(this.mapper.writeValueAsString(JsonContent.NULL)).isEqualTo("null");
		assertThat(JsonContent.of((Object) null)).isSameAs(JsonContent.NULL);
		assertThat(JsonContent.NULL.isNull()).isTrue();
	}

	@Test
	void readsEveryShapeBack() {
		assertThat(this.mapper.readValue("\"Calm\"", JsonContent.class).asText()).isEqualTo("Calm");
		assertThat(this.mapper.readValue("{\"what\":\"Charges\"}", JsonContent.class).asMap()).containsEntry("what",
				"Charges");
		assertThat(this.mapper.readValue("[\"a\",\"b\"]", JsonContent.class).asList()).containsExactly("a", "b");
		assertThat(this.mapper.readValue("null", JsonContent.class)).isNull();
	}

	@Test
	void accessorsReturnEmptyRatherThanThrowingOnTheWrongShape() {
		JsonContent text = JsonContent.of("Calm");
		assertThat(text.asMap()).isEmpty();
		assertThat(text.asList()).isEmpty();
		assertThat(JsonContent.of(Map.of("a", 1)).asText()).isNull();
	}

	@Test
	void unwrapsRatherThanNesting() {
		JsonContent once = JsonContent.of("Calm");
		assertThat(JsonContent.of((Object) once)).isSameAs(once);
	}

	@Test
	void rendersDisplayText() {
		assertThat(JsonContent.of("Very angry").toDisplayString()).isEqualTo("Very angry");
		assertThat(JsonContent.NULL.toDisplayString()).isEmpty();
		assertThat(JsonContent.of(Map.of("summary", "One change")).toDisplayString()).contains("summary");
	}

	@Test
	void rendersAStringBareSoQuotedRubricLevelsReadCorrectly() {
		// Load-bearing: ScoreAnswer.nearestLabel() and the judge's rubric wording quote
		// this straight into model-facing feedback, where "Calm" with quotes would read
		// wrongly.
		assertThat(JsonContent.of("Calm").toDisplayString()).isEqualTo("Calm");
	}

	@Test
	void rendersAContainerAsJsonRatherThanJavaToString() {
		assertThat(JsonContent.of(Map.of("summary", "One change")).toDisplayString())
			.isEqualTo("{\"summary\":\"One change\"}");
		assertThat(JsonContent.of(List.of("a", "b")).toDisplayString()).isEqualTo("[\"a\",\"b\"]");
	}

	@Test
	void rendersAPojoAsTheJsonItSerializesTo() {
		// The defect this guards: these strings are fed back to a model as the
		// description of a criterion, so they must describe what was sent, not Java
		// record syntax.
		assertThat(JsonContent.of(new Money("EUR", 1250)).toDisplayString()).isEqualTo("\"EUR 1250\"");
		assertThat(JsonContent.of(new Level("Calm", 0)).toDisplayString())
			.isEqualTo("{\"name\":\"Calm\",\"ordinal\":0}");
		assertThat(JsonContent.of(Tier.GOLD).toDisplayString()).isEqualTo("\"GOLD\"");
	}

	@Test
	void fallsBackRatherThanThrowingOnAValueJacksonCannotWrite() {
		// toDisplayString is used in exception messages and logs; it must never throw.
		JsonContent unserializable = JsonContent.of(new Object() {
			@Override
			public String toString() {
				return "opaque";
			}
		});

		assertThat(unserializable.toDisplayString()).isNotNull();
	}

	@Test
	void serializesAPojoThroughItsOwnJsonValue() {
		assertThat(this.mapper.writeValueAsString(JsonContent.of(new Money("EUR", 1250)))).isEqualTo("\"EUR 1250\"");
		assertThat(this.mapper.writeValueAsString(JsonContent.of(new Level("Calm", 0))))
			.isEqualTo("{\"name\":\"Calm\",\"ordinal\":0}");
		assertThat(this.mapper.writeValueAsString(JsonContent.of(Map.of("paid", new Money("EUR", 1250)))))
			.isEqualTo("{\"paid\":\"EUR 1250\"}");
	}

	@Test
	void accessorsReportTheJavaValueNotTheJsonItWouldProduce() {
		// Documented, deliberate divergence: `Money` serializes to a JSON string, but it
		// is not a String, so asText() says null. `as(...)` is the Jackson view.
		JsonContent money = JsonContent.of(new Money("EUR", 1250));

		assertThat(money.asText()).isNull();
		assertThat(money.asMap()).isEmpty();
		assertThat(money.asList()).isEmpty();
		assertThat(money.isNull()).isFalse();
		assertThat(money.as(String.class, this.mapper)).isEqualTo("EUR 1250");
	}

	/** A type whose JSON form is a string rather than an object. */
	record Money(String currency, long cents) {
		@JsonValue
		String toJson() {
			return this.currency + " " + this.cents;
		}
	}

	/** A plain record, whose JSON form is an object. */
	record Level(String name, int ordinal) {
	}

	enum Tier {

		GOLD

	}

}
