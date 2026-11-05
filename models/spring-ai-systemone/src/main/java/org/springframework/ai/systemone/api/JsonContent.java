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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.util.Assert;

/**
 * The polymorphic JSON payload that the System One API accepts wherever the documentation
 * refers to an {@code EntryType}: {@code state}, every {@code instructions} value and
 * every {@code criteria} description may be a string, a JSON object, a JSON array or
 * {@code null}.
 *
 * <p>
 * The wrapped value is serialized transparently (via {@link JsonValue}), so a
 * {@code JsonContent} holding a {@code String} writes as a bare JSON string and one
 * holding a {@code Map} writes as a JSON object. Anything Jackson can serialize may be
 * wrapped — an enum, a record, a {@code java.time} value or a type carrying its own
 * {@link JsonValue} — provided it produces one of the three shapes above.
 *
 * <p>
 * <strong>A bare scalar is not an {@code EntryType}.</strong> The API accepts only a
 * string, an object, an array or {@code null}, and answers {@code 422} for anything else,
 * so a {@code Number}, {@code Boolean} or a {@code @JsonValue} type that serializes to
 * one of those is rejected on the wire even though it wraps without complaint here.
 * Whether a given POJO is valid is a question about the JSON it produces, which only
 * Jackson can answer, so it is not checked at construction.
 *
 * <p>
 * Note that {@link #asText()}, {@link #asMap()} and {@link #asList()} inspect the Java
 * value rather than the JSON it would serialize to; see their documentation.
 *
 * @param value the wrapped value; a {@code String}, {@code Map}, {@code List}, any POJO
 * Jackson serializes to a string, object or array, or {@code null}
 * @author Christian Tzolov
 * @since 2.1.0
 */
public record JsonContent(@JsonValue @Nullable Object value) {

	/** Shared instance representing an explicit JSON {@code null}. */
	public static final JsonContent NULL = new JsonContent(null);

	/**
	 * Used only by {@link #toDisplayString()}. Deliberately plain: rendering for a human
	 * or a log should not depend on how the application configured its own mapper.
	 */
	private static final JsonMapper DISPLAY_MAPPER = JsonMapper.builder().build();

	/**
	 * Wraps an arbitrary value. Also the Jackson entry point when reading a
	 * {@code JsonContent} back from a response.
	 * @param value the value to wrap; may be {@code null}
	 * @return the wrapped value, never {@code null}
	 */
	@JsonCreator
	public static JsonContent of(@Nullable Object value) {
		if (value instanceof JsonContent content) {
			return content;
		}
		return value == null ? NULL : new JsonContent(value);
	}

	/**
	 * A JSON object whose keys keep the order they are written in:
	 * {@code JsonContent.object("question", "...", "inspect", "message")}.
	 *
	 * <p>
	 * Use it instead of {@code Map.of} for a state, structured instructions or a criteria
	 * description with more than one key. The model reads them in the order they are
	 * sent, and a different order can change an answer; {@code Map.of} iterates in an
	 * order that changes with every JVM run, so the same code would send a different
	 * request each time.
	 * @param keysAndValues alternating keys and values; each key a non-empty
	 * {@code String}, each value anything a {@code JsonContent} can wrap, or {@code null}
	 * for a JSON {@code null}
	 * @return the object, backed by an unmodifiable insertion-ordered map
	 * @throws IllegalArgumentException when the arguments are not key-value pairs, a key
	 * is not a non-empty string, or a key repeats
	 */
	public static JsonContent object(@Nullable Object... keysAndValues) {
		Assert.notNull(keysAndValues, "keysAndValues must not be null");
		Assert.isTrue(keysAndValues.length % 2 == 0,
				"keysAndValues must alternate keys and values, but has an odd number of arguments");
		Map<String, @Nullable Object> object = new LinkedHashMap<>();
		for (int i = 0; i < keysAndValues.length; i += 2) {
			Object key = keysAndValues[i];
			int position = i / 2;
			Assert.isTrue(key instanceof String name && !name.isEmpty(),
					() -> "key at position " + position + " must be a non-empty String, but was " + key);
			Assert.isTrue(!object.containsKey(key), () -> "duplicate key '" + key + "'");
			object.put((String) key, keysAndValues[i + 1]);
		}
		return new JsonContent(Collections.unmodifiableMap(object));
	}

	/**
	 * @return {@code true} when this instance wraps a JSON {@code null}
	 */
	public boolean isNull() {
		return this.value == null;
	}

	/**
	 * Reads the wrapped value as text. This is the <em>Java</em> view: a POJO that
	 * serializes to a JSON string still answers {@code null} here, because the value is
	 * not a {@code String}. Use {@link #as(Class, JsonMapper)} for the Jackson view.
	 * @return the wrapped value as text, or {@code null} when it is not a {@code String}
	 */
	public @Nullable String asText() {
		return this.value instanceof String text ? text : null;
	}

	/**
	 * Reads the wrapped value as a map. This is the <em>Java</em> view: a POJO that
	 * serializes to a JSON object still answers an empty map here, because the value is
	 * not a {@code Map}. Note also that an empty result does not distinguish "not an
	 * object" from "an empty object" — use {@link #as(Class, JsonMapper)} when that
	 * matters.
	 * @return the wrapped value as a map, or an empty map when it is not a {@code Map}
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> asMap() {
		return this.value instanceof Map<?, ?> map ? (Map<String, Object>) map : Collections.emptyMap();
	}

	/**
	 * Reads the wrapped value as a list. This is the <em>Java</em> view, with the same
	 * caveats as {@link #asMap()}.
	 * @return the wrapped value as a list, or an empty list when it is not a {@code List}
	 */
	@SuppressWarnings("unchecked")
	public List<Object> asList() {
		return this.value instanceof List<?> list ? (List<Object>) list : Collections.emptyList();
	}

	/**
	 * Converts the wrapped value into the given type using Jackson — the JSON view, as
	 * opposed to the {@code instanceof} checks the other accessors perform.
	 *
	 * <p>
	 * Because the conversion goes through Jackson it honours the value's own
	 * {@link JsonValue}, so a type that serializes to a string <em>is</em> a string as
	 * far as this method is concerned and converting it to a {@code Map} throws rather
	 * than yielding its fields.
	 * @param <T> the target type
	 * @param type the target type
	 * @param jsonMapper the mapper used for the conversion
	 * @return the converted value, or {@code null} when this instance wraps a JSON
	 * {@code null}
	 */
	public <T> @Nullable T as(Class<T> type, JsonMapper jsonMapper) {
		return this.value == null ? null : jsonMapper.convertValue(this.value, type);
	}

	/**
	 * Renders the wrapped value as a short human readable string, used when the client
	 * has to describe a criterion in a log or a feedback message.
	 *
	 * <p>
	 * A string renders bare, without quotes, because that is what reads well when a
	 * rubric level or a criterion description is quoted back. Everything else renders as
	 * the JSON it serializes to, so what a model is told about a value matches what was
	 * sent rather than Java's {@code toString()} syntax.
	 *
	 * <p>
	 * The mapper used here is not the one the transport serializes the request with, so a
	 * value that depends on application-configured Jackson modules can render slightly
	 * differently from what goes on the wire. A value that cannot be serialized at all
	 * falls back to {@code toString()}: this method is used in exception messages and
	 * logs and must never throw.
	 * @return a display string, never {@code null}
	 */
	public String toDisplayString() {
		if (this.value == null) {
			return "";
		}
		if (this.value instanceof String text) {
			return text;
		}
		try {
			return DISPLAY_MAPPER.writeValueAsString(this.value);
		}
		catch (RuntimeException ex) {
			return String.valueOf(this.value);
		}
	}
}
