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

package org.springframework.ai.util.json.schema

import com.fasterxml.jackson.annotation.JsonValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.ai.tool.annotation.Tool
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

/**
 * @author Sebastien Deleuze
 */
class JsonSchemaGeneratorKotlinTests {

	private val jsonMapper = JsonMapper()

	@Test
	fun `non-null property with default value should not be required`() {
		val schema = JsonSchemaGenerator.generateForType(FilterWithDefaultValue::class.java)
		val schemaNode = jsonMapper.readTree(schema)
		val required = requiredNames(schemaNode["required"])
		assertThat(required).doesNotContain("name", "ids")
	}

	@Test
	fun `nullable properties are not required in tool input schemas`() {
		val method = SearchTools::class.java.getMethod("search", Filter::class.java)

		val schema = JsonSchemaGenerator.generateForMethodInput(method)
		val schemaNode = jsonMapper.readTree(schema)
		val filterNode = schemaNode["properties"]["filter"]
		val topLevelRequired = schemaNode["required"]
		val filterRequired = filterNode["required"]

		assertThat(requiredNames(topLevelRequired)).doesNotContain("filter")
		assertThat(requiredNames(filterRequired)).doesNotContain("name", "ids")
	}

	@Test
	fun `non-null constructor properties without defaults remain required`() {
		val method = SearchTools::class.java.getMethod("mixed", SearchRequest::class.java)

		val schema = JsonSchemaGenerator.generateForMethodInput(method)
		val schemaNode = jsonMapper.readTree(schema)
		val requestNode = schemaNode["properties"]["request"]
		val requestRequired = requiredNames(requestNode["required"])

		assertThat(requestRequired).contains("query")
		assertThat(requestRequired).doesNotContain("filter")
	}

	@Test
	fun `suspend functions do not expose the continuation parameter`() {
		val method = SearchTools::class.java.declaredMethods.first { it.name == "fetch" }

		val schema = JsonSchemaGenerator.generateForMethodInput(method)
		val schemaNode = jsonMapper.readTree(schema)
		val properties = schemaNode["properties"]

		assertThat(properties["url"]).isNotNull()
		assertThat(properties["\$completion"]).isNull()
		assertThat(requiredNames(schemaNode["required"])).containsExactly("url")
	}

	// gh-1985: victools resolves @JsonValue from methods, so Kotlin properties must use
	// the get-site target for their values to surface in the generated schema.
	@Test
	fun `enum values follow get-site JsonValue annotation`() {
		val schema = JsonSchemaGenerator.generateForType(UnitHolder::class.java)
		val schemaNode = jsonMapper.readTree(schema)
		val values = schemaNode.at("/properties/unit/enum").iterator().asSequence().map { it.asString() }.toList()

		assertThat(values).containsExactly("C", "F")
	}

	private fun requiredNames(required: JsonNode?): List<String> {
		if (required == null || required.isNull) {
			return emptyList()
		}
		return required.iterator().asSequence().map { it.asString() }.toList()
	}

	private data class Filter(val name: String?, val ids: List<String>?)

	private data class FilterWithDefaultValue(val name: String?, val ids: List<String> = emptyList())

	private data class SearchRequest(val query: String, val filter: String?)

	private data class UnitHolder(val unit: TemperatureUnit)

	enum class TemperatureUnit(@get:JsonValue val symbol: String) {

		CELSIUS("C"), FAHRENHEIT("F")

	}

	private class SearchTools {

		@Tool(description = "Search")
		fun search(filter: Filter?): String {
			return "ok"
		}

		@Tool(description = "Mixed")
		fun mixed(request: SearchRequest?): String {
			return "ok"
		}

		@Tool(description = "Fetch")
		suspend fun fetch(url: String): String {
			return url
		}
	}

}
