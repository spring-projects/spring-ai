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

package org.springframework.ai.mcp.annotation.method.tool.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

class McpJsonSchemaGeneratorKotlinTests {

	private val jsonMapper = JsonMapper()

	@Test
	fun `nullable Kotlin properties are not required in MCP tool input schemas`() {
		val method = SearchTools::class.java.getMethod("search", Filter::class.java)

		val schema = McpJsonSchemaGenerator.generateForMethodInput(method)
		val schemaNode = jsonMapper.readTree(schema)
		val filterNode = schemaNode["properties"]["filter"]
		val topLevelRequired = schemaNode["required"]
		val filterRequired = filterNode["required"]

		assertThat(requiredNames(topLevelRequired)).doesNotContain("filter")
		assertThat(requiredNames(filterRequired)).doesNotContain("name", "ids")
	}

	@Test
	fun `non-null Kotlin constructor properties without defaults remain required`() {
		val method = SearchTools::class.java.getMethod("mixed", SearchRequest::class.java)

		val schema = McpJsonSchemaGenerator.generateForMethodInput(method)
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

	private data class Filter(val name: String? = null, val ids: List<String>? = null)

	private data class SearchRequest(val query: String, val filter: String? = null)

	private class SearchTools {

		@McpTool(description = "Search")
		fun search(@McpToolParam(required = false) filter: Filter? = null): String {
			return "ok"
		}

		@McpTool(description = "Mixed")
		fun mixed(@McpToolParam(required = false) request: SearchRequest? = null): String {
			return "ok"
		}

	}

}
