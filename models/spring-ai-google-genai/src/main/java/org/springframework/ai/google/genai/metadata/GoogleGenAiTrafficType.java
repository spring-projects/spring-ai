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

package org.springframework.ai.google.genai.metadata;

import com.fasterxml.jackson.annotation.JsonValue;
import com.google.genai.types.TrafficType;

/**
 * Represents the traffic type for Google GenAI requests, indicating whether a request
 * consumes Pay-As-You-Go or Provisioned Throughput quota.
 *
 * @author Dan Dobrin
 * @since 1.1.0
 */
public enum GoogleGenAiTrafficType {

	/**
	 * Pay-As-You-Go traffic type.
	 */
	ON_DEMAND("ON_DEMAND"),

	/**
	 * Provisioned Throughput traffic type.
	 */
	PROVISIONED_THROUGHPUT("PROVISIONED_THROUGHPUT"),

	/**
	 * Unknown or unspecified traffic type.
	 */
	UNKNOWN("UNKNOWN");

	private final String value;

	GoogleGenAiTrafficType(String value) {
		this.value = value;
	}

	/**
	 * Creates a GoogleGenAiTrafficType from the SDK's TrafficType.
	 * @param trafficType the SDK traffic type
	 * @return the corresponding GoogleGenAiTrafficType
	 */
	public static GoogleGenAiTrafficType from(TrafficType trafficType) {
		if (trafficType == null) {
			return UNKNOWN;
		}

		// Try to match by string value
		String typeStr = trafficType.toString().toUpperCase();

		// Map SDK values to our enum values
		return switch (typeStr) {
			case "ON_DEMAND" -> ON_DEMAND;
			case "PROVISIONED_THROUGHPUT" -> PROVISIONED_THROUGHPUT;
			case "TRAFFIC_TYPE_UNSPECIFIED" -> UNKNOWN;
			default -> {
				// Try exact match
				for (GoogleGenAiTrafficType type : values()) {
					if (type.value.equals(typeStr)) {
						yield type;
					}
				}
				yield UNKNOWN;
			}
		};
	}

	/**
	 * Returns the string value of the traffic type.
	 * @return the traffic type value
	 */
	@JsonValue
	public String getValue() {
		return this.value;
	}

	@Override
	public String toString() {
		return this.value;
	}

}
