/*
 * Copyright 2023-2023 the original author or authors.
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

package org.springframework.ai.model;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.junit.jupiter.api.Test;

import org.springframework.boot.test.json.JsonContentAssert;

/**
 * @author Christian Tzolov
 */
public class ModelOptionsUtilsTests {

	@JsonInclude(Include.NON_NULL)
	@JsonClassDescription("Weather API request")
	public record Request(@JsonProperty(required = true,
			value = "location") @JsonPropertyDescription("The city and state e.g. San Francisco, CA") String location,
			@JsonProperty(required = true, value = "lat") @JsonPropertyDescription("The city latitude") double lat,
			@JsonProperty(required = true, value = "lon") @JsonPropertyDescription("The city longitude") double lon,
			@JsonProperty(required = true, value = "unit") @JsonPropertyDescription("Temperature unit") Unit unit) {
	}

	public enum Unit {

		/**
		 * Celsius.
		 */
		c("metric"),
		/**
		 * Fahrenheit.
		 */
		f("imperial");

		/**
		 * Human readable unit name.
		 */
		public final String unitName;

		private Unit(String text) {
			this.unitName = text;
		}

	}

	@Test
	public void schemaGeneration() {

		String jsonSchema = ModelOptionsUtils.getJsonSchema(Request.class);

		System.out.println(jsonSchema);

		var expectedJsonSchema = """
				{
					"type": "object",
					"properties": {
						"location": {
							"type": "string",
							"description": "The city and state e.g. San Francisco, CA"
						},
						"lat": {
							"type": "number",
							"description": "The city latitude"
						},
						"lon": {
							"type": "number",
							"description": "The city longitude"
						},
						"unit": {
							"type": "string",
							"enum": ["c", "f"]
						}
					},
					"required": ["location", "lat", "lon", "unit"]
				}
				""";

		new JsonContentAssert(null, jsonSchema).isEqualToJson(expectedJsonSchema);
	}

}
