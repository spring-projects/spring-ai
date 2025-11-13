/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.util.json.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for JsonParser and ModelOptionsUtils ObjectMapper.
 *
 * @author Daniel Albuquerque
 */
@ConfigurationProperties(prefix = JsonParserProperties.CONFIG_PREFIX)
public class JsonParserProperties {

	public static final String CONFIG_PREFIX = "spring.ai.json";

	/**
	 * Allow unescaped control characters (like \n) in JSON strings. Useful when LLMs
	 * generate JSON with literal newlines.
	 */
	private boolean allowUnescapedControlChars = false;

	/**
	 * Write dates as ISO-8601 strings instead of timestamp arrays. When false (default),
	 * dates are written as strings like "2025-07-03".
	 */
	private boolean writeDatesAsTimestamps = false;

	/**
	 * Accept empty strings as null objects during deserialization. Used by
	 * ModelOptionsUtils for handling API responses.
	 */
	private boolean acceptEmptyStringAsNull = true;

	/**
	 * Coerce empty strings to null for enum types. Critical for handling API responses
	 * with empty finish_reason values.
	 */
	private boolean coerceEmptyEnumStrings = true;

	/**
	 * Fail on unknown properties during deserialization. When false (default), unknown
	 * properties are ignored.
	 */
	private boolean failOnUnknownProperties = false;

	/**
	 * Fail on empty beans during serialization. When false (default), empty beans are
	 * serialized as empty objects.
	 */
	private boolean failOnEmptyBeans = false;

	public boolean isAllowUnescapedControlChars() {
		return this.allowUnescapedControlChars;
	}

	public void setAllowUnescapedControlChars(boolean allowUnescapedControlChars) {
		this.allowUnescapedControlChars = allowUnescapedControlChars;
	}

	public boolean isWriteDatesAsTimestamps() {
		return this.writeDatesAsTimestamps;
	}

	public void setWriteDatesAsTimestamps(boolean writeDatesAsTimestamps) {
		this.writeDatesAsTimestamps = writeDatesAsTimestamps;
	}

	public boolean isAcceptEmptyStringAsNull() {
		return this.acceptEmptyStringAsNull;
	}

	public void setAcceptEmptyStringAsNull(boolean acceptEmptyStringAsNull) {
		this.acceptEmptyStringAsNull = acceptEmptyStringAsNull;
	}

	public boolean isCoerceEmptyEnumStrings() {
		return this.coerceEmptyEnumStrings;
	}

	public void setCoerceEmptyEnumStrings(boolean coerceEmptyEnumStrings) {
		this.coerceEmptyEnumStrings = coerceEmptyEnumStrings;
	}

	public boolean isFailOnUnknownProperties() {
		return this.failOnUnknownProperties;
	}

	public void setFailOnUnknownProperties(boolean failOnUnknownProperties) {
		this.failOnUnknownProperties = failOnUnknownProperties;
	}

	public boolean isFailOnEmptyBeans() {
		return this.failOnEmptyBeans;
	}

	public void setFailOnEmptyBeans(boolean failOnEmptyBeans) {
		this.failOnEmptyBeans = failOnEmptyBeans;
	}

}
