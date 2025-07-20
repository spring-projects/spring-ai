/*
 * Copyright 2024-2025 the original author or authors.
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

package org.springframework.ai.tool.support;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.junit.jupiter.api.Test;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ToolDefinitions}.
 *
 * @author Seol-JY
 */
class ToolDefinitionsTests {

	static class TestToolClass {

		@Tool(name = "getCurrentWeather", description = "Get current weather information")
		public String getCurrentWeather(@ToolParam(description = "The city name") String city,
				@ToolParam(description = "Temperature unit") String unit) {
			return "Weather data for " + city + " in " + unit;
		}

		@Tool(description = "Calculate sum of two numbers")
		public int calculateSum(int a, int b) {
			return a + b;
		}

		public String nonToolMethod(String input) {
			return "Not a tool method";
		}

		@Tool(description = "Process person data")
		public String processPerson(PersonData person) {
			return "Processing: " + person.name();
		}

	}

	record PersonData(@JsonProperty("full_name") @JsonPropertyDescription("The person's full name") String name,
			@JsonPropertyDescription("The person's age") int age) {
	}

	@Test
	void builderShouldCreateValidBuilderForToolMethod() throws Exception {
		Method method = TestToolClass.class.getMethod("getCurrentWeather", String.class, String.class);

		DefaultToolDefinition.Builder builder = ToolDefinitions.builder(method);
		ToolDefinition toolDefinition = builder.build();

		assertThat(toolDefinition).isNotNull();
		assertThat(toolDefinition.name()).isEqualTo("getCurrentWeather");
		assertThat(toolDefinition.description()).isEqualTo("Get current weather information");
		assertThat(toolDefinition.inputSchema()).isNotNull();
		assertThat(toolDefinition.inputSchema().toString()).contains("city").contains("unit");
	}

	@Test
	void builderShouldCreateValidBuilderForMethodWithoutNameAnnotation() throws Exception {
		Method method = TestToolClass.class.getMethod("calculateSum", int.class, int.class);

		DefaultToolDefinition.Builder builder = ToolDefinitions.builder(method);
		ToolDefinition toolDefinition = builder.build();

		assertThat(toolDefinition).isNotNull();
		assertThat(toolDefinition.name()).isEqualTo("calculateSum");
		assertThat(toolDefinition.description()).isEqualTo("Calculate sum of two numbers");
		assertThat(toolDefinition.inputSchema()).isNotNull();
	}

	@Test
	void builderShouldCreateValidBuilderForMethodWithComplexParameter() throws Exception {
		Method method = TestToolClass.class.getMethod("processPerson", PersonData.class);

		DefaultToolDefinition.Builder builder = ToolDefinitions.builder(method);
		ToolDefinition toolDefinition = builder.build();

		assertThat(toolDefinition).isNotNull();
		assertThat(toolDefinition.name()).isEqualTo("processPerson");
		assertThat(toolDefinition.description()).isEqualTo("Process person data");
		assertThat(toolDefinition.inputSchema()).isNotNull();
		assertThat(toolDefinition.inputSchema().toString()).contains("full_name").contains("age");
	}

	@Test
	void builderShouldThrowExceptionWhenMethodIsNull() {
		assertThatThrownBy(() -> ToolDefinitions.builder(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("method cannot be null");
	}

	@Test
	void fromShouldCreateValidToolDefinition() throws Exception {
		Method method = TestToolClass.class.getMethod("getCurrentWeather", String.class, String.class);

		ToolDefinition toolDefinition = ToolDefinitions.from(method);

		assertThat(toolDefinition).isNotNull();
		assertThat(toolDefinition.name()).isEqualTo("getCurrentWeather");
		assertThat(toolDefinition.description()).isEqualTo("Get current weather information");
		assertThat(toolDefinition.inputSchema()).isNotNull();
	}

	@Test
	void fromShouldCreateConsistentToolDefinitions() throws Exception {
		Method method = TestToolClass.class.getMethod("getCurrentWeather", String.class, String.class);

		ToolDefinition toolDefinition1 = ToolDefinitions.from(method);
		ToolDefinition toolDefinition2 = ToolDefinitions.from(method);

		assertThat(toolDefinition1).isEqualTo(toolDefinition2);
		assertThat(toolDefinition1.name()).isEqualTo(toolDefinition2.name());
		assertThat(toolDefinition1.description()).isEqualTo(toolDefinition2.description());
		assertThat(toolDefinition1.inputSchema()).isEqualTo(toolDefinition2.inputSchema());
	}

	@Test
	void fromShouldThrowExceptionWhenMethodIsNull() {
		assertThatThrownBy(() -> ToolDefinitions.from(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("method cannot be null");
	}

	@Test
	void fromShouldReturnSameInstanceForSameMethod() throws Exception {
		Method method = TestToolClass.class.getMethod("getCurrentWeather", String.class, String.class);

		ToolDefinition toolDefinition1 = ToolDefinitions.from(method);
		ToolDefinition toolDefinition2 = ToolDefinitions.from(method);

		assertThat(toolDefinition1).isSameAs(toolDefinition2);
	}

	@Test
	void fromShouldReturnDifferentInstancesForDifferentMethods() throws Exception {
		Method method1 = TestToolClass.class.getMethod("getCurrentWeather", String.class, String.class);
		Method method2 = TestToolClass.class.getMethod("calculateSum", int.class, int.class);

		ToolDefinition toolDefinition1 = ToolDefinitions.from(method1);
		ToolDefinition toolDefinition2 = ToolDefinitions.from(method2);

		assertThat(toolDefinition1).isNotSameAs(toolDefinition2);
		assertThat(toolDefinition1.name()).isNotEqualTo(toolDefinition2.name());
	}

	@Test
	void cachingShouldBeThreadSafe() throws Exception {
		Method method = TestToolClass.class.getMethod("getCurrentWeather", String.class, String.class);
		ExecutorService executor = Executors.newFixedThreadPool(10);
		int numberOfTasks = 100;

		CompletableFuture<ToolDefinition>[] futures = new CompletableFuture[numberOfTasks];
		for (int i = 0; i < numberOfTasks; i++) {
			futures[i] = CompletableFuture.supplyAsync(() -> ToolDefinitions.from(method), executor);
		}

		CompletableFuture<Void> allFutures = CompletableFuture.allOf(futures);
		allFutures.get(5, TimeUnit.SECONDS);

		ToolDefinition first = futures[0].get();
		for (int i = 1; i < numberOfTasks; i++) {
			assertThat(futures[i].get()).isSameAs(first);
		}

		executor.shutdown();
	}

	@Test
	void fromShouldHandleMethodWithNoParameters() throws Exception {
		class NoParamToolClass {

			@Tool(description = "Get system status")
			public String getSystemStatus() {
				return "OK";
			}

		}
		Method method = NoParamToolClass.class.getMethod("getSystemStatus");

		ToolDefinition toolDefinition = ToolDefinitions.from(method);

		assertThat(toolDefinition).isNotNull();
		assertThat(toolDefinition.name()).isEqualTo("getSystemStatus");
		assertThat(toolDefinition.description()).isEqualTo("Get system status");
		assertThat(toolDefinition.inputSchema()).isNotNull();
	}

	@Test
	void fromShouldHandleMethodWithMultipleComplexParameters() throws Exception {
		class ComplexParamToolClass {

			@Tool(description = "Process complex data")
			public String processComplexData(PersonData person, String[] tags, int priority) {
				return "Processed";
			}

		}
		Method method = ComplexParamToolClass.class.getMethod("processComplexData", PersonData.class, String[].class,
				int.class);

		ToolDefinition toolDefinition = ToolDefinitions.from(method);

		assertThat(toolDefinition).isNotNull();
		assertThat(toolDefinition.name()).isEqualTo("processComplexData");
		assertThat(toolDefinition.description()).isEqualTo("Process complex data");
		assertThat(toolDefinition.inputSchema()).isNotNull();
	}

	@Test
	void fromShouldHandleOverloadedMethods() throws Exception {
		class OverloadedToolClass {

			@Tool(description = "Process string")
			public String process(String input) {
				return "String: " + input;
			}

			@Tool(description = "Process number")
			public String process(int input) {
				return "Number: " + input;
			}

		}

		Method stringMethod = OverloadedToolClass.class.getMethod("process", String.class);
		Method intMethod = OverloadedToolClass.class.getMethod("process", int.class);

		ToolDefinition stringToolDefinition = ToolDefinitions.from(stringMethod);
		ToolDefinition intToolDefinition = ToolDefinitions.from(intMethod);

		assertThat(stringToolDefinition).isNotSameAs(intToolDefinition);
		assertThat(stringToolDefinition.name()).isEqualTo("process");
		assertThat(intToolDefinition.name()).isEqualTo("process");
		assertThat(stringToolDefinition.description()).isEqualTo("Process string");
		assertThat(intToolDefinition.description()).isEqualTo("Process number");
	}

	@Test
	void builderAndFromShouldProduceEquivalentResults() throws Exception {
		Method method = TestToolClass.class.getMethod("getCurrentWeather", String.class, String.class);

		ToolDefinition fromBuilder = ToolDefinitions.builder(method).build();
		ToolDefinition fromMethod = ToolDefinitions.from(method);

		assertThat(fromBuilder.name()).isEqualTo(fromMethod.name());
		assertThat(fromBuilder.description()).isEqualTo(fromMethod.description());
		assertThat(fromBuilder.inputSchema()).isEqualTo(fromMethod.inputSchema());
	}

}
