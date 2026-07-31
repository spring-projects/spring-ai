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

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.ToolExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link MethodToolCallback} ToolContext handling (issue #6545).
 *
 * <p>
 * Covers: null context, empty context, non-empty context, multiple values, null values,
 * case sensitivity, methods that don't accept ToolContext, and exception paths.
 */
class MethodToolCallbackToolContextTests {

	private ContextAwareTools toolObject;

	private MethodToolCallback contextCallback;

	private MethodToolCallback noContextCallback;

	@BeforeEach
	void setUp() throws Exception {
		this.toolObject = new ContextAwareTools();

		Method contextMethod = ContextAwareTools.class.getMethod("withContext", String.class, ToolContext.class);
		ToolDefinition contextDef = DefaultToolDefinition.builder()
			.name("withContext")
			.description("Tool that reads ToolContext")
			.inputSchema("{\"type\":\"object\",\"properties\":{\"input\":{\"type\":\"string\"}}}")
			.build();
		this.contextCallback = MethodToolCallback.builder()
			.toolDefinition(contextDef)
			.toolMethod(contextMethod)
			.toolObject(this.toolObject)
			.build();

		Method noContextMethod = ContextAwareTools.class.getMethod("withoutContext", String.class);
		ToolDefinition noContextDef = DefaultToolDefinition.builder()
			.name("withoutContext")
			.description("Tool that ignores ToolContext")
			.inputSchema("{\"type\":\"object\",\"properties\":{\"input\":{\"type\":\"string\"}}}")
			.build();
		this.noContextCallback = MethodToolCallback.builder()
			.toolDefinition(noContextDef)
			.toolMethod(noContextMethod)
			.toolObject(this.toolObject)
			.build();
	}

	// -------------------------------------------------------------------------
	// null ToolContext
	// -------------------------------------------------------------------------

	@Test
	void nullToolContextYieldsEmptyContextInsideTool() {
		// call(toolInput) overload passes null — tool must receive an empty ToolContext
		String result = this.contextCallback.call("{\"input\": \"ping\"}");
		assertThat(result).contains("size=0");
	}

	@Test
	void nullToolContextExplicitlyPassedYieldsEmptyContextInsideTool() {
		String result = this.contextCallback.call("{\"input\": \"ping\"}", null);
		assertThat(result).contains("size=0");
	}

	// -------------------------------------------------------------------------
	// empty ToolContext (the primary bug from issue #6545)
	// -------------------------------------------------------------------------

	@Test
	void emptyToolContextIsAccepted() {
		ToolContext empty = new ToolContext(new HashMap<>());
		String result = this.contextCallback.call("{\"input\": \"ping\"}", empty);
		assertThat(result).contains("size=0");
	}

	@Test
	void emptyToolContextViaMapOfIsAccepted() {
		ToolContext empty = new ToolContext(Map.of());
		String result = this.contextCallback.call("{\"input\": \"test\"}", empty);
		assertThat(result).contains("size=0");
	}

	// -------------------------------------------------------------------------
	// non-empty ToolContext — backward compatibility
	// -------------------------------------------------------------------------

	@Test
	void singleEntryContextPassedThrough() {
		ToolContext ctx = new ToolContext(Map.of("tenantId", "t-1"));
		String result = this.contextCallback.call("{\"input\": \"hi\"}", ctx);
		assertThat(result).contains("tenantId=t-1").contains("size=1");
	}

	@Test
	void multipleEntriesAllPassedThrough() {
		Map<String, Object> map = new HashMap<>();
		map.put("tenantId", "t-1");
		map.put("requestId", "r-42");
		map.put("locale", "en-US");
		ToolContext ctx = new ToolContext(map);

		String result = this.contextCallback.call("{\"input\": \"hi\"}", ctx);
		assertThat(result).contains("size=3").contains("tenantId=t-1").contains("requestId=r-42");
	}

	// -------------------------------------------------------------------------
	// case sensitivity of context keys
	// -------------------------------------------------------------------------

	@Test
	void contextKeysCaseIsPreserved() {
		ToolContext ctx = new ToolContext(Map.of("UserId", "upper", "userid", "lower"));
		String result = this.contextCallback.call("{\"input\": \"case\"}", ctx);
		assertThat(result).contains("size=2");
	}

	// -------------------------------------------------------------------------
	// methods that do NOT accept ToolContext
	// -------------------------------------------------------------------------

	@Test
	void nullContextIgnoredForMethodWithoutContextParam() {
		String result = this.noContextCallback.call("{\"input\": \"hello\"}", null);
		assertThat(result).contains("hello");
	}

	@Test
	void emptyContextIgnoredForMethodWithoutContextParam() {
		ToolContext empty = new ToolContext(Map.of());
		String result = this.noContextCallback.call("{\"input\": \"world\"}", empty);
		assertThat(result).contains("world");
	}

	@Test
	void nonEmptyContextIgnoredForMethodWithoutContextParam() {
		ToolContext ctx = new ToolContext(Map.of("key", "value"));
		String result = this.noContextCallback.call("{\"input\": \"ignored\"}", ctx);
		assertThat(result).contains("ignored");
	}

	// -------------------------------------------------------------------------
	// exception path — tool throws, context does not affect exception wrapping
	// -------------------------------------------------------------------------

	@Test
	void toolExceptionWithEmptyContextIsWrappedCorrectly() throws Exception {
		ThrowingTools throwing = new ThrowingTools();
		Method method = ThrowingTools.class.getMethod("alwaysFails", String.class, ToolContext.class);
		ToolDefinition def = DefaultToolDefinition.builder()
			.name("alwaysFails")
			.description("Always throws")
			.inputSchema("{\"type\":\"object\",\"properties\":{\"input\":{\"type\":\"string\"}}}")
			.build();
		MethodToolCallback failCallback = MethodToolCallback.builder()
			.toolDefinition(def)
			.toolMethod(method)
			.toolObject(throwing)
			.build();

		ToolContext empty = new ToolContext(Map.of());
		assertThatThrownBy(() -> failCallback.call("{\"input\": \"boom\"}", empty))
			.isInstanceOf(ToolExecutionException.class)
			.hasMessageContaining("deliberate failure for: boom");
	}

	@Test
	void toolExceptionWithNullContextIsWrappedCorrectly() throws Exception {
		ThrowingTools throwing = new ThrowingTools();
		Method method = ThrowingTools.class.getMethod("alwaysFails", String.class, ToolContext.class);
		ToolDefinition def = DefaultToolDefinition.builder()
			.name("alwaysFails")
			.description("Always throws")
			.inputSchema("{\"type\":\"object\",\"properties\":{\"input\":{\"type\":\"string\"}}}")
			.build();
		MethodToolCallback failCallback = MethodToolCallback.builder()
			.toolDefinition(def)
			.toolMethod(method)
			.toolObject(throwing)
			.build();

		assertThatThrownBy(() -> failCallback.call("{\"input\": \"boom\"}", null))
			.isInstanceOf(ToolExecutionException.class)
			.hasMessageContaining("deliberate failure for: boom");
	}

	// -------------------------------------------------------------------------
	// Tool classes
	// -------------------------------------------------------------------------

	public static class ContextAwareTools {

		@Tool(description = "Tool that reads ToolContext")
		public String withContext(String input, ToolContext toolContext) {
			Map<String, Object> ctx = toolContext.getContext();
			StringBuilder sb = new StringBuilder();
			sb.append("input=").append(input).append(" size=").append(ctx.size());
			boolean hasOrder = ctx.containsKey("orderNumber") && ctx.get("orderNumber") != null;
			sb.append(" hasOrder=").append(hasOrder);
			ctx.forEach((k, v) -> {
				if (!"orderNumber".equals(k)) {
					sb.append(" ").append(k).append("=").append(v);
				}
			});
			return sb.toString();
		}

		@Tool(description = "Tool that ignores ToolContext")
		public String withoutContext(String input) {
			return "echo:" + input;
		}

	}

	public static class ThrowingTools {

		@Tool(description = "Always throws")
		public String alwaysFails(String input, ToolContext toolContext) {
			throw new RuntimeException("deliberate failure for: " + input);
		}

	}

}
