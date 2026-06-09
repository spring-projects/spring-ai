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

package org.springframework.ai.mcp.annotation.method.elicitation;

import java.lang.reflect.Method;
import java.util.Map;

import io.modelcontextprotocol.spec.McpSchema.ElicitRequest;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import org.junit.jupiter.api.Test;

import org.springframework.ai.mcp.annotation.McpElicitation;
import org.springframework.ai.mcp.annotation.method.elicitation.AbstractMcpElicitationMethodCallback.McpElicitationMethodException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link SyncMcpElicitationMethodCallback}.
 *
 * @author Christian Tzolov
 */
public class SyncMcpElicitationMethodCallbackTests {

	private final SyncMcpElicitationMethodCallbackExample example = new SyncMcpElicitationMethodCallbackExample();

	@Test
	void testValidMethodAccept() throws Exception {
		Method method = SyncMcpElicitationMethodCallbackExample.class.getMethod("handleElicitationRequest",
				ElicitRequest.class);
		McpElicitation annotation = method.getAnnotation(McpElicitation.class);
		assertThat(annotation).isNotNull();

		SyncMcpElicitationMethodCallback callback = SyncMcpElicitationMethodCallback.builder()
			.method(method)
			.bean(this.example)
			.build();

		ElicitRequest request = ElicitationTestHelper.createSampleRequest();
		ElicitResult result = callback.apply(request);

		assertThat(result).isNotNull();
		assertThat(result.action()).isEqualTo(ElicitResult.Action.ACCEPT);
		assertThat(result.content()).isNotNull();
		assertThat(result.content()).containsEntry("userInput", "Example user input");
		assertThat(result.content()).containsEntry("confirmed", true);
	}

	@Test
	void testValidMethodDecline() throws Exception {
		Method method = SyncMcpElicitationMethodCallbackExample.class.getMethod("handleDeclineElicitationRequest",
				ElicitRequest.class);
		McpElicitation annotation = method.getAnnotation(McpElicitation.class);
		assertThat(annotation).isNotNull();

		SyncMcpElicitationMethodCallback callback = SyncMcpElicitationMethodCallback.builder()
			.method(method)
			.bean(this.example)
			.build();

		ElicitRequest request = ElicitationTestHelper.createSampleRequest();
		ElicitResult result = callback.apply(request);

		assertThat(result).isNotNull();
		assertThat(result.action()).isEqualTo(ElicitResult.Action.DECLINE);
		assertThat(result.content()).isNull();
	}

	@Test
	void testValidMethodCancel() throws Exception {
		Method method = SyncMcpElicitationMethodCallbackExample.class.getMethod("handleCancelElicitationRequest",
				ElicitRequest.class);
		McpElicitation annotation = method.getAnnotation(McpElicitation.class);
		assertThat(annotation).isNotNull();

		SyncMcpElicitationMethodCallback callback = SyncMcpElicitationMethodCallback.builder()
			.method(method)
			.bean(this.example)
			.build();

		ElicitRequest request = ElicitationTestHelper.createSampleRequest();
		ElicitResult result = callback.apply(request);

		assertThat(result).isNotNull();
		assertThat(result.action()).isEqualTo(ElicitResult.Action.CANCEL);
		assertThat(result.content()).isNull();
	}

	@Test
	void testNullRequest() throws Exception {
		Method method = SyncMcpElicitationMethodCallbackExample.class.getMethod("handleElicitationRequest",
				ElicitRequest.class);
		McpElicitation annotation = method.getAnnotation(McpElicitation.class);

		SyncMcpElicitationMethodCallback callback = SyncMcpElicitationMethodCallback.builder()
			.method(method)
			.bean(this.example)
			.build();

		assertThatThrownBy(() -> callback.apply(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Request must not be null");
	}

	@Test
	void testInvalidReturnType() throws Exception {
		Method method = SyncMcpElicitationMethodCallbackExample.class.getMethod("invalidReturnType",
				ElicitRequest.class);
		McpElicitation annotation = method.getAnnotation(McpElicitation.class);
		assertThat(annotation).isNotNull();

		assertThatThrownBy(() -> SyncMcpElicitationMethodCallback.builder().method(method).bean(this.example).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Method must return ElicitResult");
	}

	@Test
	void testInvalidParameterType() throws Exception {
		Method method = SyncMcpElicitationMethodCallbackExample.class.getMethod("invalidParameterType", String.class);
		McpElicitation annotation = method.getAnnotation(McpElicitation.class);
		assertThat(annotation).isNotNull();

		assertThatThrownBy(() -> SyncMcpElicitationMethodCallback.builder().method(method).bean(this.example).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Single parameter must be of type ElicitRequest");
	}

	@Test
	void testNoParameters() throws Exception {
		Method method = SyncMcpElicitationMethodCallbackExample.class.getMethod("noParameters");
		McpElicitation annotation = method.getAnnotation(McpElicitation.class);
		assertThat(annotation).isNotNull();

		assertThatThrownBy(() -> SyncMcpElicitationMethodCallback.builder().method(method).bean(this.example).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Method must have at least 1 parameter");
	}

	@Test
	void testTooManyParameters() throws Exception {
		Method method = SyncMcpElicitationMethodCallbackExample.class.getMethod("tooManyParameters",
				ElicitRequest.class, String.class);
		McpElicitation annotation = method.getAnnotation(McpElicitation.class);

		assertThatThrownBy(() -> SyncMcpElicitationMethodCallback.builder().method(method).bean(this.example).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Currently only methods with a single ElicitRequest parameter are supported");
	}

	@Test
	void testNullMethod() {
		assertThatThrownBy(() -> SyncMcpElicitationMethodCallback.builder().method(null).bean(this.example).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Method must not be null");
	}

	@Test
	void testNullBean() throws Exception {
		Method method = SyncMcpElicitationMethodCallbackExample.class.getMethod("handleElicitationRequest",
				ElicitRequest.class);
		assertThatThrownBy(() -> SyncMcpElicitationMethodCallback.builder().method(method).bean(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Bean must not be null");
	}

	@Test
	void testMethodInvocationError() throws Exception {
		// Create a method that will throw an exception when invoked
		Method method = SyncMcpElicitationMethodCallbackExample.class.getMethod("handleElicitationRequest",
				ElicitRequest.class);
		McpElicitation annotation = method.getAnnotation(McpElicitation.class);

		SyncMcpElicitationMethodCallback callback = SyncMcpElicitationMethodCallback.builder()
			.method(method)
			.bean(new SyncMcpElicitationMethodCallbackExample() {
				@Override
				public ElicitResult handleElicitationRequest(ElicitRequest request) {
					throw new RuntimeException("Test exception");
				}
			})
			.build();

		ElicitRequest request = ElicitationTestHelper.createSampleRequest();
		assertThatThrownBy(() -> callback.apply(request)).isInstanceOf(McpElicitationMethodException.class)
			.hasMessageContaining("Error invoking elicitation method")
			.hasCauseInstanceOf(java.lang.reflect.InvocationTargetException.class)
			.satisfies(e -> {
				Throwable cause = e.getCause().getCause();
				assertThat(cause).isInstanceOf(RuntimeException.class);
				assertThat(cause.getMessage()).isEqualTo("Test exception");
			});
	}

	@Test
	void testBuilderValidation() {
		// Test that builder validates required fields
		assertThatThrownBy(() -> SyncMcpElicitationMethodCallback.builder().build())
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void testCustomRequestContent() throws Exception {
		Method method = SyncMcpElicitationMethodCallbackExample.class.getMethod("handleElicitationRequest",
				ElicitRequest.class);

		SyncMcpElicitationMethodCallback callback = SyncMcpElicitationMethodCallback.builder()
			.method(method)
			.bean(this.example)
			.build();

		ElicitRequest customRequest = ElicitationTestHelper.createSampleRequest("Custom prompt",
				Map.of("customKey", "customValue", "priority", "high"));
		ElicitResult result = callback.apply(customRequest);

		assertThat(result).isNotNull();
		assertThat(result.action()).isEqualTo(ElicitResult.Action.ACCEPT);
		assertThat(result.content()).isNotNull();
		assertThat(result.content()).containsEntry("userInput", "Example user input");
		assertThat(result.content()).containsEntry("confirmed", true);
	}

}
