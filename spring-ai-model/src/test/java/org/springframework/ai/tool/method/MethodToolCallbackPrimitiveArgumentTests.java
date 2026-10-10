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

package org.springframework.ai.tool.method;

import org.junit.jupiter.api.Test;

import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.execution.ToolExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression tests for GH-6723: a missing primitive tool parameter must surface as a
 * {@link ToolExecutionException} so {@code ToolExecutionExceptionProcessor} can convert
 * it into a tool result the model can read and retry from.
 *
 * @author arimu1
 */
class MethodToolCallbackPrimitiveArgumentTests {

	/** The model omitted "includeHourly" — a routine occurrence with smaller models. */
	private static final String MODEL_OUTPUT_OMITTING_PRIMITIVE = "{\"city\": \"Rome\"}";

	private static ToolCallback toolNamed(String name) {
		for (ToolCallback candidate : ToolCallbacks.from(new WeatherTools())) {
			if (candidate.getToolDefinition().name().equals(name)) {
				return candidate;
			}
		}
		throw new AssertionError("no such tool: " + name);
	}

	@Test
	void wrapperParameter_acceptsMissingAsNull() {
		ToolCallback tool = toolNamed("forecastBoxed");
		assertThat(tool.call(MODEL_OUTPUT_OMITTING_PRIMITIVE)).isEqualTo("\"Rome / hourly=null\"");
	}

	@Test
	void missingPrimitiveBoolean_throwsToolExecutionException() {
		ToolCallback tool = toolNamed("forecast");

		assertThatThrownBy(() -> tool.call(MODEL_OUTPUT_OMITTING_PRIMITIVE)).isInstanceOf(ToolExecutionException.class)
			.hasMessageContaining("includeHourly")
			.hasMessageContaining("boolean")
			.hasCauseInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void missingPrimitiveInt_throwsToolExecutionException() {
		ToolCallback tool = toolNamed("forecastDays");

		assertThatThrownBy(() -> tool.call(MODEL_OUTPUT_OMITTING_PRIMITIVE)).isInstanceOf(ToolExecutionException.class)
			.hasMessageContaining("days")
			.hasMessageContaining("int")
			.hasCauseInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void presentPrimitiveBoolean_invokesSuccessfully() {
		ToolCallback tool = toolNamed("forecast");
		assertThat(tool.call("{\"city\": \"Rome\", \"includeHourly\": true}")).isEqualTo("\"Rome / hourly=true\"");
	}

	static class WeatherTools {

		@Tool(description = "Get the forecast, optionally including the hourly breakdown.")
		String forecast(String city, boolean includeHourly) {
			return city + " / hourly=" + includeHourly;
		}

		@Tool(description = "Same tool, but the flag is a wrapper instead of a primitive.")
		String forecastBoxed(String city, Boolean includeHourly) {
			return city + " / hourly=" + includeHourly;
		}

		@Tool(description = "Forecast with a primitive int parameter.")
		String forecastDays(String city, int days) {
			return city + " / days=" + days;
		}

	}

}
