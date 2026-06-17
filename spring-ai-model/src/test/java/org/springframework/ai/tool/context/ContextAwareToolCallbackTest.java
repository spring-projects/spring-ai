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

package org.springframework.ai.tool.context;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link ContextAwareToolCallback} and {@link ToolContextHolder}.
 */
class ContextAwareToolCallbackTest {

	@Mock
	private ToolCallback mockDelegate;

	@Mock
	private ToolDefinition mockToolDefinition;

	@Mock
	private ToolContext mockToolContext;

	private AutoCloseable mocks;

	@BeforeEach
	void setUp() {
		this.mocks = MockitoAnnotations.openMocks(this);
	}

	@AfterEach
	void tearDown() throws Exception {
		ToolContextHolder.clear();
		this.mocks.close();
	}

	@Nested
	@DisplayName("ToolContextHolder")
	class ToolContextHolderTests {

		@Test
		@DisplayName("set then get returns the same context")
		void setThenGet() {
			ToolContextHolder.set(mockToolContext);
			assertThat(ToolContextHolder.get()).isSameAs(mockToolContext);
		}

		@Test
		@DisplayName("get returns null when nothing was set")
		void getReturnsNullWhenUnset() {
			assertThat(ToolContextHolder.get()).isNull();
		}

		@Test
		@DisplayName("clear removes the bound context")
		void clearRemovesContext() {
			ToolContextHolder.set(mockToolContext);
			ToolContextHolder.clear();
			assertThat(ToolContextHolder.get()).isNull();
		}

		@Test
		@DisplayName("set replaces a previously bound context")
		void setReplacesPrevious() {
			ToolContext ctx1 = new ToolContext(Map.of("k", "v1"));
			ToolContext ctx2 = new ToolContext(Map.of("k", "v2"));
			ToolContextHolder.set(ctx1);
			ToolContextHolder.set(ctx2);
			assertThat(ToolContextHolder.get()).isSameAs(ctx2);
		}

	}

	@Nested
	@DisplayName("Constructor")
	class ConstructorTests {

		@Test
		@DisplayName("delegates getToolDefinition to the wrapped callback")
		void delegatesToolDefinition() {
			when(mockDelegate.getToolDefinition()).thenReturn(mockToolDefinition);
			ContextAwareToolCallback callback = new ContextAwareToolCallback(mockDelegate);
			assertThat(callback.getToolDefinition()).isSameAs(mockToolDefinition);
		}

		@Test
		@DisplayName("rejects a null delegate")
		void rejectsNullDelegate() {
			org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
				.isThrownBy(() -> new ContextAwareToolCallback(null));
		}

	}

	@Nested
	@DisplayName("call(String)")
	class SingleArgCallTests {

		@Test
		@DisplayName("forwards to delegate.call(input, null) and clears holder after")
		void forwardsAndClears() {
			ContextAwareToolCallback callback = new ContextAwareToolCallback(mockDelegate);
			when(mockDelegate.call(eq("input"), any())).thenReturn("ok");

			String result = callback.call("input");

			assertThat(result).isEqualTo("ok");
			verify(mockDelegate).call("input", null);
			assertThat(ToolContextHolder.get()).isNull();
		}

		@Test
		@DisplayName("clears the holder even when the delegate throws")
		void clearsOnException() {
			ContextAwareToolCallback callback = new ContextAwareToolCallback(mockDelegate);
			when(mockDelegate.call(any(), any())).thenThrow(new RuntimeException("boom"));

			org.assertj.core.api.Assertions.assertThatThrownBy(() -> callback.call("input"))
				.isInstanceOf(RuntimeException.class)
				.hasMessage("boom");
			assertThat(ToolContextHolder.get()).isNull();
		}

	}

	@Nested
	@DisplayName("call(String, ToolContext)")
	class TwoArgCallTests {

		@Test
		@DisplayName("publishes the context to the holder, forwards, then clears")
		void publishesAndForwards() {
			ContextAwareToolCallback callback = new ContextAwareToolCallback(mockDelegate);
			when(mockDelegate.call("input", mockToolContext)).thenAnswer(invocation -> {
				assertThat(ToolContextHolder.get()).isSameAs(mockToolContext);
				return "ok";
			});

			String result = callback.call("input", mockToolContext);

			assertThat(result).isEqualTo("ok");
			verify(mockDelegate).call("input", mockToolContext);
			assertThat(ToolContextHolder.get()).isNull();
		}

		@Test
		@DisplayName("publishes null context to the holder when called with null")
		void publishesNullWhenNull() {
			ContextAwareToolCallback callback = new ContextAwareToolCallback(mockDelegate);
			when(mockDelegate.call("input", null)).thenAnswer(invocation -> {
				assertThat(ToolContextHolder.get()).isNull();
				return "ok";
			});

			String result = callback.call("input", null);

			assertThat(result).isEqualTo("ok");
			assertThat(ToolContextHolder.get()).isNull();
		}

		@Test
		@DisplayName("clears the holder even when the delegate throws")
		void clearsOnException() {
			ContextAwareToolCallback callback = new ContextAwareToolCallback(mockDelegate);
			when(mockDelegate.call(any(), any())).thenThrow(new RuntimeException("boom"));

			org.assertj.core.api.Assertions.assertThatThrownBy(() -> callback.call("input", mockToolContext))
				.isInstanceOf(RuntimeException.class)
				.hasMessage("boom");
			assertThat(ToolContextHolder.get()).isNull();
		}

		@Test
		@DisplayName("replaces a pre-existing holder value during invocation")
		void replacesExistingHolderValue() {
			ToolContext outer = new ToolContext(Map.of("scope", "outer"));
			ToolContextHolder.set(outer);

			ContextAwareToolCallback callback = new ContextAwareToolCallback(mockDelegate);
			when(mockDelegate.call(any(), any())).thenAnswer(invocation -> {
				assertThat(ToolContextHolder.get()).isSameAs(mockToolContext);
				return "ok";
			});

			callback.call("input", mockToolContext);

			// after the call, the holder is cleared (not restored to outer)
			assertThat(ToolContextHolder.get()).isNull();
		}

	}

	@Nested
	@DisplayName("Integration: @Tool method without ToolContext parameter")
	class IntegrationTests {

		@Test
		@DisplayName("a tool method that does not declare ToolContext can read it from the holder")
		void toolMethodReadsHolder() {
			ToolContext expected = new ToolContext(Map.of("userId", "u-42"));
			ToolContextHolder.set(expected);

			ContextAwareToolCallback callback = new ContextAwareToolCallback(mockDelegate);
			when(mockDelegate.call(any(), any())).thenAnswer(invocation -> {
				// Simulating a @Tool method that does NOT declare a ToolContext parameter
				// but reads it from the holder.
				ToolContext ctx = ToolContextHolder.get();
				assertThat(ctx).isSameAs(expected);
				assertThat(ctx.getContext()).containsEntry("userId", "u-42");
				return ctx.getContext().get("userId").toString();
			});

			String result = callback.call("input", expected);

			assertThat(result).isEqualTo("u-42");
			assertThat(ToolContextHolder.get()).isNull();
		}

	}

}
