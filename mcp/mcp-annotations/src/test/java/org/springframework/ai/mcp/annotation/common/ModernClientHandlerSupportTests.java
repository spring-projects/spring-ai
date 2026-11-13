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

package org.springframework.ai.mcp.annotation.common;

import java.lang.reflect.Method;
import java.util.Map;

import io.modelcontextprotocol.modern.McpSchema.ElicitFormRequest;
import io.modelcontextprotocol.modern.McpSchema.ElicitRequest;
import io.modelcontextprotocol.modern.McpSchema.ElicitResult;
import io.modelcontextprotocol.modern.McpSchema.ElicitUrlRequest;
import io.modelcontextprotocol.modern.McpSchema.ProgressParams;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.annotation.context.StructuredElicitResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link ModernClientHandlerSupport}. {@code McpSchema} is the schema of the
 * legacy protocol; the types imported by their simple name are those of the modern
 * revision.
 *
 * @author Christian Tzolov
 */
class ModernClientHandlerSupportTests {

	private static final Map<String, Object> SCHEMA = Map.of("type", "object");

	private static final Map<String, Object> META = Map.of("trace", "t-1");

	@Test
	void formRequestIsTheSameInBothSchemas() {
		var legacy = new McpSchema.ElicitFormRequest("Delete the ticket?", SCHEMA, META);

		ElicitRequest modern = ModernClientHandlerSupport.toModernElicitRequest(legacy);

		assertThat(modern).isEqualTo(new ElicitFormRequest("Delete the ticket?", SCHEMA, META));
		assertThat(ModernClientHandlerSupport.toLegacyElicitRequest(modern)).isEqualTo(legacy);
	}

	@Test
	void legacyUrlRequestLosesItsElicitationId() {
		var legacy = new McpSchema.ElicitUrlRequest("Log in", "https://example.com/login", "e-1", META);

		assertThat(ModernClientHandlerSupport.toModernElicitRequest(legacy))
			.isEqualTo(new ElicitUrlRequest("Log in", "https://example.com/login", META));
	}

	@Test
	void modernUrlRequestCannotBecomeALegacyOne() {
		var modern = new ElicitUrlRequest("Log in", "https://example.com/login", null);

		assertThatThrownBy(() -> ModernClientHandlerSupport.toLegacyElicitRequest(modern))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("elicitationId");
	}

	@Test
	void legacyResultLosesItsMeta() {
		var legacy = new McpSchema.ElicitResult(McpSchema.ElicitResult.Action.ACCEPT, Map.of("confirmed", true), META);

		ElicitResult modern = ModernClientHandlerSupport.toModernElicitResult(legacy);

		assertThat(modern).isEqualTo(ElicitResult.accept(Map.of("confirmed", true)));
		assertThat(ModernClientHandlerSupport.toLegacyElicitResult(modern)).isEqualTo(
				new McpSchema.ElicitResult(McpSchema.ElicitResult.Action.ACCEPT, Map.of("confirmed", true), null));
	}

	@Test
	void everyActionHasACounterpart() {
		for (McpSchema.ElicitResult.Action action : McpSchema.ElicitResult.Action.values()) {
			ElicitResult modern = ModernClientHandlerSupport
				.toModernElicitResult(new McpSchema.ElicitResult(action, null, null));

			assertThat(modern.action().name()).isEqualTo(action.name());
			assertThat(ModernClientHandlerSupport.toLegacyElicitResult(modern).action()).isEqualTo(action);
		}
	}

	@Test
	void progressIsTheSameInBothSchemas() {
		var legacy = new McpSchema.ProgressNotification("token-1", 10.0, 100.0, "asking", META);

		ProgressParams modern = ModernClientHandlerSupport.toModernProgress(legacy);

		assertThat(modern).isEqualTo(new ProgressParams("token-1", 10.0, 100.0, "asking", META));
		assertThat(ModernClientHandlerSupport.toLegacyProgress(modern)).isEqualTo(legacy);
	}

	@Test
	void methodsOfBothSchemasAnswerModernFormRequests() {
		assertThat(ModernClientHandlerSupport.handlesModernFormElicitation(method("legacyAny"))).isTrue();
		assertThat(ModernClientHandlerSupport.handlesModernFormElicitation(method("legacyForm"))).isTrue();
		assertThat(ModernClientHandlerSupport.handlesModernFormElicitation(method("modernAny"))).isTrue();
		assertThat(ModernClientHandlerSupport.handlesModernFormElicitation(method("modernForm"))).isTrue();
		assertThat(ModernClientHandlerSupport.handlesModernFormElicitation(method("structured"))).isTrue();

		assertThat(ModernClientHandlerSupport.handlesModernFormElicitation(method("legacyUrl"))).isFalse();
		assertThat(ModernClientHandlerSupport.handlesModernFormElicitation(method("modernUrl"))).isFalse();
	}

	@Test
	void onlyMethodsOfTheModernSchemaAnswerModernUrlRequests() {
		assertThat(ModernClientHandlerSupport.handlesModernUrlElicitation(method("modernAny"))).isTrue();
		assertThat(ModernClientHandlerSupport.handlesModernUrlElicitation(method("modernUrl"))).isTrue();

		assertThat(ModernClientHandlerSupport.handlesModernUrlElicitation(method("modernForm"))).isFalse();
		assertThat(ModernClientHandlerSupport.handlesModernUrlElicitation(method("legacyAny"))).isFalse();
		assertThat(ModernClientHandlerSupport.handlesModernUrlElicitation(method("legacyUrl"))).isFalse();
	}

	@Test
	void legacyRequestsAreAnsweredByTheMethodsThatTakeAFormRequest() {
		assertThat(ModernClientHandlerSupport.handlesLegacyElicitation(method("legacyAny"))).isTrue();
		assertThat(ModernClientHandlerSupport.handlesLegacyElicitation(method("legacyForm"))).isTrue();
		assertThat(ModernClientHandlerSupport.handlesLegacyElicitation(method("modernAny"))).isTrue();
		assertThat(ModernClientHandlerSupport.handlesLegacyElicitation(method("modernForm"))).isTrue();

		// A legacy client declares form elicitation only.
		assertThat(ModernClientHandlerSupport.handlesLegacyElicitation(method("legacyUrl"))).isFalse();
		assertThat(ModernClientHandlerSupport.handlesLegacyElicitation(method("modernUrl"))).isFalse();
	}

	@Test
	void methodStandsForTheKindsOfRequestItsParameterTakes() {
		for (String name : new String[] { "legacyAny", "modernAny" }) {
			assertThat(ModernClientHandlerSupport.claimsFormElicitation(method(name))).as(name).isTrue();
			assertThat(ModernClientHandlerSupport.claimsUrlElicitation(method(name))).as(name).isTrue();
		}
		for (String name : new String[] { "legacyForm", "modernForm", "structured" }) {
			assertThat(ModernClientHandlerSupport.claimsFormElicitation(method(name))).as(name).isTrue();
			assertThat(ModernClientHandlerSupport.claimsUrlElicitation(method(name))).as(name).isFalse();
		}
		for (String name : new String[] { "legacyUrl", "modernUrl" }) {
			assertThat(ModernClientHandlerSupport.claimsFormElicitation(method(name))).as(name).isFalse();
			assertThat(ModernClientHandlerSupport.claimsUrlElicitation(method(name))).as(name).isTrue();
		}
		// A method that is no synchronous handler leaves no kind of request to another.
		assertThat(ModernClientHandlerSupport.claimsFormElicitation(method("reactive"))).isTrue();
		assertThat(ModernClientHandlerSupport.claimsUrlElicitation(method("reactive"))).isTrue();
	}

	@Test
	void methodMayTakeAToolContextBeforeOrAfterTheRequest() {
		assertThat(ModernClientHandlerSupport.elicitRequestParameterType(method("modernFormWithToolContext")))
			.isEqualTo(ElicitFormRequest.class);
		assertThat(ModernClientHandlerSupport.elicitRequestParameterType(method("toolContextWithLegacyAny")))
			.isEqualTo(McpSchema.ElicitRequest.class);

		assertThat(ModernClientHandlerSupport.handlesModernFormElicitation(method("modernFormWithToolContext")))
			.isTrue();
		assertThat(ModernClientHandlerSupport.handlesModernUrlElicitation(method("modernFormWithToolContext")))
			.isFalse();
		assertThat(ModernClientHandlerSupport.handlesModernFormElicitation(method("toolContextWithLegacyAny")))
			.isTrue();
		assertThat(ModernClientHandlerSupport.handlesLegacyElicitation(method("toolContextWithLegacyAny"))).isTrue();

		assertThat(ModernClientHandlerSupport.takesToolContext(method("modernFormWithToolContext"))).isTrue();
		assertThat(ModernClientHandlerSupport.takesToolContext(method("modernForm"))).isFalse();
	}

	@Test
	void methodsThatAreNoSynchronousElicitationHandlersAnswerNothing() {
		for (String name : new String[] { "reactive", "wrongParameter", "wrongReturnType", "noParameter",
				"twoToolContexts", "extraParameter", "onlyToolContext" }) {
			Method method = method(name);

			assertThat(ModernClientHandlerSupport.handlesModernFormElicitation(method)).as(name).isFalse();
			assertThat(ModernClientHandlerSupport.handlesModernUrlElicitation(method)).as(name).isFalse();
			assertThat(ModernClientHandlerSupport.handlesLegacyElicitation(method)).as(name).isFalse();
		}
	}

	private static Method method(String name) {
		for (Method method : Handlers.class.getDeclaredMethods()) {
			if (method.getName().equals(name)) {
				return method;
			}
		}
		throw new AssertionError("no method named " + name);
	}

	@SuppressWarnings("unused")
	static class Handlers {

		McpSchema.ElicitResult legacyAny(McpSchema.ElicitRequest request) {
			return null;
		}

		McpSchema.ElicitResult legacyForm(McpSchema.ElicitFormRequest request) {
			return null;
		}

		McpSchema.ElicitResult legacyUrl(McpSchema.ElicitUrlRequest request) {
			return null;
		}

		ElicitResult modernAny(ElicitRequest request) {
			return null;
		}

		ElicitResult modernForm(ElicitFormRequest request) {
			return null;
		}

		ElicitResult modernUrl(ElicitUrlRequest request) {
			return null;
		}

		StructuredElicitResult<String> structured(ElicitFormRequest request) {
			return null;
		}

		Mono<ElicitResult> reactive(ElicitRequest request) {
			return Mono.empty();
		}

		ElicitResult wrongParameter(String request) {
			return null;
		}

		String wrongReturnType(ElicitRequest request) {
			return null;
		}

		ElicitResult noParameter() {
			return null;
		}

		ElicitResult modernFormWithToolContext(ElicitFormRequest request, ToolContext toolContext) {
			return null;
		}

		McpSchema.ElicitResult toolContextWithLegacyAny(ToolContext toolContext, McpSchema.ElicitRequest request) {
			return null;
		}

		ElicitResult twoToolContexts(ElicitRequest request, ToolContext first, ToolContext second) {
			return null;
		}

		ElicitResult extraParameter(ElicitRequest request, ToolContext toolContext, String extra) {
			return null;
		}

		ElicitResult onlyToolContext(ToolContext toolContext) {
			return null;
		}

	}

}
