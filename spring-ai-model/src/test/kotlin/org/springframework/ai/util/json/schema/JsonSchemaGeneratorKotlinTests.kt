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

	private fun requiredNames(required: JsonNode?): List<String> {
		if (required == null || required.isNull) {
			return emptyList()
		}
		return required.iterator().asSequence().map { it.asString() }.toList()
	}

	private data class Filter(val name: String?, val ids: List<String>?)

	private data class FilterWithDefaultValue(val name: String?, val ids: List<String> = emptyList())

	private data class SearchRequest(val query: String, val filter: String?)

	private class SearchTools {

		@Tool(description = "Search")
		fun search(filter: Filter?): String {
			return "ok"
		}

		@Tool(description = "Mixed")
		fun mixed(request: SearchRequest?): String {
			return "ok"
		}
	}

}
