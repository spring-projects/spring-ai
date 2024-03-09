/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.model.function;

import java.util.function.Function;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.junit.jupiter.api.Test;

import org.springframework.ai.model.function.TypeResolverHelperTests.MockWeatherService.Request;
import org.springframework.ai.model.function.TypeResolverHelperTests.MockWeatherService.Response;

import static org.assertj.core.api.Assertions.assertThat;;

/**
 * @author Christian Tzolov
 */
public class TypeResolverHelperTests {

	@Test
	public void testGetFunctionInputType() {
		Class<?> inputType = TypeResolverHelper.getFunctionInputClass(MockWeatherService.class);
		assertThat(inputType).isEqualTo(Request.class);
	}

	@Test
	public void testGetFunctionOutputType() {
		Class<?> outputType = TypeResolverHelper.getFunctionOutputClass(MockWeatherService.class);
		assertThat(outputType).isEqualTo(Response.class);
	}

	@Test
	public void testGetFunctionInputTypeForInstance() {
		MockWeatherService service = new MockWeatherService();
		Class<?> inputType = TypeResolverHelper.getFunctionInputClass(service.getClass());
		assertThat(inputType).isEqualTo(Request.class);
	}

	public static class OutputFunctionConverter implements Function<Response, String> {

		@Override
		public String apply(Response response) {
			return response.temp + " " + response.unit;
		}

	}

	public static class MockWeatherService implements Function<Request, Response> {

		/**
		 * Weather Function request.
		 */
		@JsonInclude(Include.NON_NULL)
		@JsonClassDescription("Weather API request")
		public record Request(@JsonProperty(required = true,
				value = "location") @JsonPropertyDescription("The city and state e.g. San Francisco, CA") String location,
				@JsonProperty(required = true, value = "lat") @JsonPropertyDescription("The city latitude") double lat,
				@JsonProperty(required = true, value = "lon") @JsonPropertyDescription("The city longitude") double lon,
				@JsonProperty(required = true,
						value = "unit") @JsonPropertyDescription("Temperature unit") String unit) {
		}

		public record Response(double temp, String unit) {
		}

		@Override
		public Response apply(Request request) {
			return new Response(10, "C");
		}

	}

}
