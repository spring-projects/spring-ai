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

package org.springframework.ai.mcp.annotation.method.tool.utils;

import java.lang.reflect.Method;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import org.springframework.ai.util.JsonHelper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link McpJsonSchemaGenerator}.
 */
class McpJsonSchemaGeneratorTests {

	private static final JsonHelper jsonHelper = new JsonHelper();

	// gh-5888: when a method parameter type transitively contains a recursive type,
	// victools emits $defs nested inside the parameter sub-schema while $ref values
	// remain root-relative ("#/$defs/<Name>"). Inlining the sub-schema under
	// properties.<paramName> would otherwise leave those $refs unresolvable.
	// The generator must hoist $defs to the outer schema root.
	@Test
	void generateSchemaForMethodWithTransitivelyRecursiveParameterTypeHoistsDefs() throws Exception {
		Method method = TestMethods.class.getDeclaredMethod("searchBooksMethod", SearchRequest.class);

		String schema = McpJsonSchemaGenerator.generateForMethodInput(method);
		JsonNode schemaNode = jsonHelper.fromJson(schema, JsonNode.class);

		assertThat(schemaNode.has("$defs")).as("$defs must be hoisted to the outer schema root").isTrue();
		assertThat(schemaNode.get("$defs").has("RecursiveFilter")).isTrue();
		assertThat(schemaNode.at("/properties/request").has("$defs"))
			.as("$defs must not remain nested inside the parameter sub-schema")
			.isFalse();
		assertThat(schemaNode.at("/properties/request/properties/filters/items/$ref").asString())
			.isEqualTo("#/$defs/RecursiveFilter");
		assertThat(schemaNode.at("/$defs/RecursiveFilter/properties/filters/items/$ref").asString())
			.isEqualTo("#/$defs/RecursiveFilter");
	}

	// gh-5888: when two parameters share the same recursive type, the two
	// generated $defs entries collide on an identical key and value. The hoist
	// must reuse the single root entry; both parameters' $refs continue to
	// resolve to it.
	@Test
	void generateSchemaForMethodReusesRootDefsWhenCollidingDefsValuesAreEqual() throws Exception {
		Method method = TestMethods.class.getDeclaredMethod("sameOuterTwoParamsMethod", SearchRequest.class,
				SearchRequest.class);

		String schema = McpJsonSchemaGenerator.generateForMethodInput(method);
		JsonNode schemaNode = jsonHelper.fromJson(schema, JsonNode.class);

		assertThat(schemaNode.at("/$defs").size()).isEqualTo(1);
		assertThat(schemaNode.at("/$defs").has("RecursiveFilter")).isTrue();
		assertThat(schemaNode.at("/properties/a/properties/filters/items/$ref").asString())
			.isEqualTo("#/$defs/RecursiveFilter");
		assertThat(schemaNode.at("/properties/b/properties/filters/items/$ref").asString())
			.isEqualTo("#/$defs/RecursiveFilter");
	}

	// gh-5888: when two parameters carry different recursive types that share
	// the same simple class name, victools (with Option.PLAIN_DEFINITION_KEYS)
	// emits the same $defs key for both. First-wins would silently drop the
	// second definition and leave its $ref pointing at the first definition.
	// The hoist must rename the colliding entry and rewrite the inlined
	// sub-schema's $refs to point at the new key.
	@Test
	void generateSchemaForMethodRenamesDefsAndRewritesRefsOnSimpleNameCollision() throws Exception {
		Method method = TestMethods.class.getDeclaredMethod("collidingSimpleNameMethod", OuterA.SearchRequest.class,
				OuterB.SearchRequest.class);

		String schema = McpJsonSchemaGenerator.generateForMethodInput(method);
		JsonNode schemaNode = jsonHelper.fromJson(schema, JsonNode.class);

		assertThat(schemaNode.at("/$defs/Filter").has("properties")).isTrue();
		assertThat(schemaNode.at("/$defs/Filter_2").has("properties")).isTrue();
		assertThat(schemaNode.at("/$defs/Filter/properties").has("label")).isTrue();
		assertThat(schemaNode.at("/$defs/Filter_2/properties").has("code")).isTrue();
		assertThat(schemaNode.at("/properties/a/properties/filters/items/$ref").asString()).isEqualTo("#/$defs/Filter");
		assertThat(schemaNode.at("/properties/b/properties/filters/items/$ref").asString())
			.isEqualTo("#/$defs/Filter_2");
		assertThat(schemaNode.at("/$defs/Filter/properties/children/items/$ref").asString())
			.isEqualTo("#/$defs/Filter");
		assertThat(schemaNode.at("/$defs/Filter_2/properties/children/items/$ref").asString())
			.isEqualTo("#/$defs/Filter_2");
	}

	// gh-5888: when a sub-schema brings in several $defs entries and one of them
	// collides while a peer entry references the colliding key, the peer's $ref
	// must be rewritten too.
	@Test
	void generateSchemaForMethodRewritesPeerDefinitionRefsAfterRename() throws Exception {
		Method method = TestMethods.class.getDeclaredMethod("peerReferenceMethod", PeerA.SearchRequest.class,
				PeerB.SearchRequest.class);

		String schema = McpJsonSchemaGenerator.generateForMethodInput(method);
		JsonNode schemaNode = jsonHelper.fromJson(schema, JsonNode.class);

		assertThat(schemaNode.at("/$defs/Filter/properties").has("label")).isTrue();
		assertThat(schemaNode.at("/$defs/Filter_2/properties").has("code")).isTrue();
		assertThat(schemaNode.at("/$defs/Wrapper").has("properties")).isTrue();
		assertThat(schemaNode.at("/$defs/Wrapper/properties/filters/items/$ref").asString())
			.isEqualTo("#/$defs/Filter_2");
		assertThat(schemaNode.at("/$defs/Wrapper/properties/nested/items/$ref").asString())
			.isEqualTo("#/$defs/Wrapper");
		assertThat(schemaNode.at("/$defs/Filter_2/properties/children/items/$ref").asString())
			.isEqualTo("#/$defs/Filter_2");
	}

	// gh-1985: enum constants annotated with @JsonProperty must surface their custom
	// values in the generated schema, matching Jackson's deserialization behavior.
	@Test
	void generateSchemaForMethodWithCustomEnumValues() throws Exception {
		Method method = TestMethods.class.getDeclaredMethod("useCustomEnum", CustomEnum.class);

		String schema = McpJsonSchemaGenerator.generateForMethodInput(method);
		JsonNode schemaNode = jsonHelper.fromJson(schema, JsonNode.class);

		assertThat(schemaNode.at("/properties/status/enum")).map(JsonNode::asString)
			.containsExactly("value.a", "value.b");
	}

	// gh-1985: enums serialized through a @JsonValue method must surface those values
	// in the generated schema as well.
	@Test
	void generateSchemaForMethodWithJsonValueEnum() throws Exception {
		Method method = TestMethods.class.getDeclaredMethod("useJsonValueEnum", TemperatureUnit.class);

		String schema = McpJsonSchemaGenerator.generateForMethodInput(method);
		JsonNode schemaNode = jsonHelper.fromJson(schema, JsonNode.class);

		assertThat(schemaNode.at("/properties/unit/enum")).map(JsonNode::asString).containsExactly("C", "F");
	}

	@Test
	void generateForMethodInputThrowsWhenMethodIsNull() {
		assertThatThrownBy(() -> McpJsonSchemaGenerator.generateForMethodInput(null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("method cannot be null");
	}

	@Test
	void generateFromClassProducesValidObjectSchema() {
		String schema = McpJsonSchemaGenerator.generateFromClass(SearchRequest.class);
		JsonNode schemaNode = jsonHelper.fromJson(schema, JsonNode.class);

		assertThat(schemaNode.get("type").asString()).isEqualTo("object");
		assertThat(schemaNode.has("properties")).isTrue();
	}

	@Test
	void generateFromTypeProducesValidObjectSchema() {
		String schema = McpJsonSchemaGenerator.generateFromType(SearchRequest.class);
		JsonNode schemaNode = jsonHelper.fromJson(schema, JsonNode.class);

		assertThat(schemaNode.get("type").asString()).isEqualTo("object");
		assertThat(schemaNode.has("properties")).isTrue();
	}

	static class TestMethods {

		public void searchBooksMethod(SearchRequest request) {
		}

		public void sameOuterTwoParamsMethod(SearchRequest a, SearchRequest b) {
		}

		public void collidingSimpleNameMethod(OuterA.SearchRequest a, OuterB.SearchRequest b) {
		}

		public void peerReferenceMethod(PeerA.SearchRequest a, PeerB.SearchRequest b) {
		}

		public void useCustomEnum(CustomEnum status) {
		}

		public void useJsonValueEnum(TemperatureUnit unit) {
		}

	}

	enum CustomEnum {

		@JsonProperty("value.a")
		A, @JsonProperty("value.b")
		B

	}

	public enum TemperatureUnit {

		CELSIUS("C"), FAHRENHEIT("F");

		private final String symbol;

		TemperatureUnit(String symbol) {
			this.symbol = symbol;
		}

		@JsonValue
		public String symbol() {
			return this.symbol;
		}

	}

	record RecursiveFilter(String field, String operator, List<RecursiveFilter> filters) {
	}

	record SearchRequest(List<RecursiveFilter> filters, int limit) {
	}

	static class OuterA {

		record Filter(String label, List<Filter> children) {
		}

		record SearchRequest(List<Filter> filters, int limit) {
		}

	}

	static class OuterB {

		record Filter(String code, List<Filter> children) {
		}

		record SearchRequest(List<Filter> filters, int limit) {
		}

	}

	static class PeerA {

		record Filter(String label, List<Filter> children) {
		}

		record SearchRequest(List<Filter> filters) {
		}

	}

	static class PeerB {

		record Filter(String code, List<Filter> children) {
		}

		record Wrapper(List<Filter> filters, List<Wrapper> nested) {
		}

		record SearchRequest(Wrapper wrapper) {
		}

	}

}
