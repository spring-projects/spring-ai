/*
 * Copyright 2024-2024 the original author or authors.
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

package org.springframework.ai.model.function;

import java.util.function.BiFunction;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import org.springframework.ai.model.function.FunctionCallback.FunctionInvokingSpec;
import org.springframework.ai.model.function.FunctionCallback.MethodInvokingSpec;
import org.springframework.core.ParameterizedTypeReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link DefaultFunctionCallbackBuilder}.
 *
 * @author Christian Tzolov
 */
class DefaultFunctionCallbackBuilderTests {

	// Common

	@Test
	void whenDescriptionIsNullThenThrow() {
		assertThatThrownBy(() -> FunctionCallback.builder().description(null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Description must not be empty");
	}

	@Test
	void whenDescriptionIsEmptyThenThrow() {
		assertThatThrownBy(() -> FunctionCallback.builder().description(""))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Description must not be empty");
	}

	@Test
	void whenInputTypeSchemaIsNullThenThrow() {
		assertThatThrownBy(() -> FunctionCallback.builder().inputTypeSchema(null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("InputTypeSchema must not be empty");
	}

	@Test
	void whenInputTypeSchemaIsEmptyThenThrow() {
		assertThatThrownBy(() -> FunctionCallback.builder().inputTypeSchema(""))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("InputTypeSchema must not be empty");
	}

	@Test
	void whenSchemaTypeIsNullThenThrow() {
		assertThatThrownBy(() -> FunctionCallback.builder().schemaType(null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("SchemaType must not be null");
	}

	@Test
	void whenResponseConverterIsNullThenThrow() {
		assertThatThrownBy(() -> FunctionCallback.builder().responseConverter(null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("ResponseConverter must not be null");
	}

	// Function
	@Test
	void whenFunctionNameIsNullThenThrow2() {
		assertThatThrownBy(() -> FunctionCallback.builder().function(null, (Function) null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Name must not be empty");
	}

	@Test
	void whenFunctionIsNullThenThrow() {
		assertThatThrownBy(() -> FunctionCallback.builder().function("functionName", (Function) null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Function must not be null");
	}

	@Test
	void whenFunctionThenReturn() {
		FunctionInvokingSpec<?, ?> functionBuilder = FunctionCallback.builder()
			.function("functionName", input -> "output");
		assertThat(functionBuilder).isNotNull();
	}

	@Test
	void whenFunctionWithNullInputTypeThenThrow() {
		assertThatThrownBy(() -> FunctionCallback.builder().function("functionName", input -> "output").build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("InputType must not be null");
	}

	@Test
	void whenFunctionWithInputTypeThenReturn() {
		FunctionCallback functionCallback = FunctionCallback.builder()
			.description("description")
			.function("functionName", input -> "output")
			.inputType(String.class)
			.build();
		assertThat(functionCallback).isNotNull();
		assertThat(functionCallback.getDescription()).isEqualTo("description");
		assertThat(functionCallback.getName()).isEqualTo("functionName");
		assertThat(functionCallback.getInputTypeSchema()).isNotEmpty();
	}

	@Test
	void whenFunctionWithGeneratedDescriptionThenReturn() {
		FunctionCallback functionCallback = FunctionCallback.builder()
			.function("veryLongDescriptiveFunctionName", input -> "output")
			.inputType(String.class)
			.build();
		assertThat(functionCallback.getDescription()).isEqualTo("very long descriptive function name");
		assertThat(functionCallback.getName()).isEqualTo("veryLongDescriptiveFunctionName");
	}

	@Test
	void whenFunctionWithGenericInputTypeThenReturn() {
		FunctionCallback functionCallback = FunctionCallback.builder()
			.function("functionName", input -> "output")
			.inputType(new ParameterizedTypeReference<GenericsRequest<Request>>() {
			})
			.build();
		assertThat(functionCallback.getName()).isEqualTo("functionName");
		assertThat(functionCallback.getInputTypeSchema()).isEqualTo("""
				{
				  "$schema" : "https://json-schema.org/draft/2020-12/schema",
				  "type" : "object",
				  "properties" : {
				    "datum" : {
				      "type" : "object",
				      "properties" : {
				        "value" : {
				          "type" : "string"
				        }
				      }
				    }
				  }
				}""");
	}

	// BiFunction
	@Test
	void whenBiFunctionNameIsNullThenThrow2() {
		assertThatThrownBy(() -> FunctionCallback.builder().function(null, (BiFunction) null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Name must not be empty");
	}

	@Test
	void whenBiFunctionIsNullThenThrow() {
		assertThatThrownBy(() -> FunctionCallback.builder().function("functionName", (BiFunction) null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("BiFunction must not be null");
	}

	@Test
	void whenBiFunctionThenReturn() {
		FunctionInvokingSpec<?, ?> functionBuilder = FunctionCallback.builder()
			.function("functionName", (input, context) -> "output");
		assertThat(functionBuilder).isNotNull();
	}

	// Method
	@Test
	void whenMethodNameIsNullThenThrow() {
		assertThatThrownBy(() -> FunctionCallback.builder().method(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Method name must not be null");
	}

	@Test
	void whenMethodArgumentTypesIsNullThenThrow() {
		assertThatThrownBy(() -> FunctionCallback.builder().method("methodName", null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Argument types must not be null");
	}

	@Test
	void whenMethodThenReturn() {
		MethodInvokingSpec methodInvokeBuilder = FunctionCallback.builder().method("methodName");
		assertThat(methodInvokeBuilder).isNotNull();
	}

	@Test
	void whenMethodWithArgumentTypesThenReturn() {
		MethodInvokingSpec methodInvokeBuilder = FunctionCallback.builder()
			.method("methodName", String.class, Integer.class);
		assertThat(methodInvokeBuilder).isNotNull();
	}

	@Test
	void whenMethodWithMissingTargetObjectOrTargetClassThenThrow() {
		assertThatThrownBy(() -> FunctionCallback.builder().method("methodName").build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Target class or object must not be null");
	}

	@Test
	void whenMethodWithMissingTargetObjectThenThrow() {
		assertThatThrownBy(() -> FunctionCallback.builder()
			.method("methodName", String.class, Integer.class)
			.targetClass(TestClass.class)
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Function object must be provided for non-static methods!");
	}

	@Test
	void whenMethodNotExistingThenThrow() {
		assertThatThrownBy(() -> FunctionCallback.builder().method("methodName").targetClass(TestClass.class).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Method: 'methodName' with arguments:[] not found!");
	}

	@Test
	void whenMethodAndNameIsNullThenThrow() {
		assertThatThrownBy(() -> FunctionCallback.builder()
			.method("staticMethodName", String.class, Integer.class)
			.targetClass(TestClass.class)
			.name(null)
			.build()).isInstanceOf(IllegalArgumentException.class).hasMessage("Name must not be empty");
	}

	@Test
	void whenMethodAndTargetClassThenReturn() {
		var functionCallback = FunctionCallback.builder()
			.method("staticMethodName", String.class, Integer.class)
			.targetClass(TestClass.class)
			.build();
		assertThat(functionCallback).isNotNull();
	}

	@Test
	void whenMethodAndTargetObjectThenReturn() {
		var functionCallback = FunctionCallback.builder()
			.method("methodName", String.class, Integer.class)
			.targetObject(new TestClass())
			.build();
		assertThat(functionCallback).isNotNull();
	}

	public static class TestClass {

		public static String staticMethodName(String arg1, Integer arg2) {
			return arg1 + arg2;
		}

		public String methodName(String arg1, Integer arg2) {
			return arg1 + arg2;
		}

	}

	public record Request(String value) {
	}

	public static class GenericsRequest<T> {

		private T datum;

		public T getDatum() {
			return datum;
		}

		public void setDatum(T value) {
			this.datum = value;
		}

	}

}
