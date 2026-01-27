/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.tool.function;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.execution.ToolCallResult;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.ai.tool.metadata.ToolMetadata;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author YunKui Lu
 */
class FunctionToolCallbackTest {

	@Test
	void testConsumerToolCall() {
		TestFunctionTool tool = new TestFunctionTool();
		FunctionToolCallback<String, Void> callback = FunctionToolCallback.builder("testTool", tool.stringConsumer())
			.toolMetadata(ToolMetadata.builder().returnDirect(true).build())
			.description("test description")
			.inputType(String.class)
			.build();

		callback.call("\"test string param\"");

		assertEquals("test string param", tool.calledValue.get());
	}

	@Test
	void testBiFunctionToolCall() {
		TestFunctionTool tool = new TestFunctionTool();
		FunctionToolCallback<String, String> callback = FunctionToolCallback
			.builder("testTool", tool.stringBiFunction())
			.toolMetadata(ToolMetadata.builder().returnDirect(true).build())
			.description("test description")
			.inputType(String.class)
			.build();

		ToolContext toolContext = new ToolContext(Map.of("foo", "bar"));

		ToolCallResult callResult = callback.call("\"test string param\"", toolContext);

		assertEquals("test string param", tool.calledValue.get());
		assertEquals("\"return value = test string param\"", callResult.content());
		assertEquals(toolContext, tool.calledToolContext.get());
	}

	@Test
	void testFunctionToolCall() {
		TestFunctionTool tool = new TestFunctionTool();
		FunctionToolCallback<String, String> callback = FunctionToolCallback.builder("testTool", tool.stringFunction())
			.toolMetadata(ToolMetadata.builder().returnDirect(true).build())
			.description("test description")
			.inputType(String.class)
			.build();

		ToolContext toolContext = new ToolContext(Map.of());

		ToolCallResult callResult = callback.call("\"test string param\"", toolContext);

		assertEquals("test string param", tool.calledValue.get());
		assertEquals("\"return value = test string param\"", callResult.content());
	}

	@Test
	void testSupplierToolCall() {
		TestFunctionTool tool = new TestFunctionTool();

		FunctionToolCallback<Void, String> callback = FunctionToolCallback.builder("testTool", tool.stringSupplier())
			.toolMetadata(ToolMetadata.builder().returnDirect(true).build())
			.description("test description")
			.inputType(Void.class)
			.build();

		ToolContext toolContext = new ToolContext(Map.of());

		ToolCallResult callResult = callback.call("\"test string param\"", toolContext);

		assertEquals("not params", tool.calledValue.get());
		assertEquals("\"return value = \"", callResult.content());
	}

	@Test
	void testThrowRuntimeException() {
		TestFunctionTool tool = new TestFunctionTool();
		FunctionToolCallback<String, Void> callback = FunctionToolCallback
			.builder("testTool", tool.throwRuntimeException())
			.toolMetadata(ToolMetadata.builder().returnDirect(true).build())
			.description("test description")
			.inputType(String.class)
			.build();

		assertThatThrownBy(() -> callback.call("\"test string param\"")).hasMessage("test exception")
			.hasCauseInstanceOf(RuntimeException.class)
			.asInstanceOf(type(ToolExecutionException.class))
			.extracting(ToolExecutionException::getToolDefinition)
			.isEqualTo(callback.getToolDefinition());
	}

	@Test
	void testThrowToolExecutionException() {
		TestFunctionTool tool = new TestFunctionTool();
		FunctionToolCallback<String, Void> callback = FunctionToolCallback
			.builder("testTool", tool.throwToolExecutionException())
			.toolMetadata(ToolMetadata.builder().returnDirect(true).build())
			.description("test description")
			.inputType(String.class)
			.build();

		assertThatThrownBy(() -> callback.call("\"test string param\"")).hasMessage("test exception")
			.hasCauseInstanceOf(RuntimeException.class)
			.isInstanceOf(ToolExecutionException.class);
	}

	@Test
	void testEmptyStringInput() {
		TestFunctionTool tool = new TestFunctionTool();
		FunctionToolCallback<String, Void> callback = FunctionToolCallback.builder("testTool", tool.stringConsumer())
			.description("test empty string")
			.inputType(String.class)
			.build();

		callback.call("\"\"");
		assertEquals("", tool.calledValue.get());
	}

	static class TestFunctionTool {

		AtomicReference<Object> calledValue = new AtomicReference<>();

		AtomicReference<ToolContext> calledToolContext = new AtomicReference<>();

		public Consumer<String> stringConsumer() {
			return s -> this.calledValue.set(s);
		}

		public BiFunction<String, ToolContext, String> stringBiFunction() {
			return (s, context) -> {
				this.calledValue.set(s);
				this.calledToolContext.set(context);
				return "return value = " + s;
			};
		}

		public Function<String, String> stringFunction() {
			return s -> {
				this.calledValue.set(s);
				return "return value = " + s;
			};
		}

		public Supplier<String> stringSupplier() {
			this.calledValue.set("not params");
			return () -> "return value = ";
		}

		public Consumer<String> throwRuntimeException() {
			return s -> {
				throw new RuntimeException("test exception");
			};
		}

		public Consumer<String> throwToolExecutionException() {
			return s -> {
				throw new ToolExecutionException(null, new RuntimeException("test exception"));
			};
		}

	}

}
