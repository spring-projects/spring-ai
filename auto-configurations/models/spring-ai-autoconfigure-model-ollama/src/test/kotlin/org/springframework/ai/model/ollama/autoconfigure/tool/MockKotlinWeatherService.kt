/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.model.ollama.autoconfigure.tool

import com.fasterxml.jackson.annotation.JsonClassDescription
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include
import com.fasterxml.jackson.annotation.JsonPropertyDescription

class MockKotlinWeatherService : Function1<KotlinRequest, KotlinResponse> {

	override fun invoke(kotlinRequest: KotlinRequest): KotlinResponse {
		var temperature = 10.0
		if (kotlinRequest.location.contains("Paris")) {
			temperature = 15.0
		}
		else if (kotlinRequest.location.contains("Tokyo")) {
			temperature = 10.0
		}
		else if (kotlinRequest.location.contains("San Francisco")) {
			temperature = 30.0
		}

		return KotlinResponse(temperature, 15.0, 20.0, 2.0, 53, 45, Unit.C);
	}
}

/**
 * Temperature units.
 */
enum class Unit(val unitName: String) {

	/**
	 * Celsius.
	 */
	C("metric"),
	/**
	 * Fahrenheit.
	 */
	F("imperial");
}

/**
 * Weather Function request.
 */
@JsonInclude(Include.NON_NULL)
@JsonClassDescription("Weather API request")
data class KotlinRequest(

	@get:JsonPropertyDescription("The city and state e.g. San Francisco, CA")
	val location: String,

	@get:JsonPropertyDescription("The city latitude")
	val lat: Double,

	@get:JsonPropertyDescription("The city longitude")
	val lon: Double,

	@get:JsonPropertyDescription("Temperature unit")
	val unit: Unit = Unit.C
)


/**
 * Weather Function response.
 */
data class KotlinResponse(val temp: Double,
						  val feels_like: Double,
						  val temp_min: Double,
						  val temp_max: Double,
						  val pressure: Int,
						  val humidity: Int,
						  val unit: Unit
)
