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

package org.springframework.ai.mcp.annotation.provider.tool

import java.util.UUID

import io.modelcontextprotocol.common.McpTransportContext
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest
import io.modelcontextprotocol.spec.McpSchema.TextContent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam

class SyncStatelessMcpToolProviderKotlinTests {

	@Test
	fun `deserializes a Kotlin data class tool argument`() {
		val provider = SyncStatelessMcpToolProvider(listOf(SearchTools()))
		val tool = provider.getToolSpecifications().single()
		val userId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
		val request = CallToolRequest.builder("search-users")
			.arguments(mapOf("request" to mapOf("userId" to userId.toString(), "username" to "alex")))
			.build()

		val result = tool.callHandler().apply(McpTransportContext.EMPTY, request)

		assertThat(result.isError()).isFalse()
		assertThat(result.content()).containsExactly(TextContent("$userId:alex:10"))
	}

	private data class SearchRequest(val userId: UUID, val username: String? = null, val limit: Int = 10)

	private class SearchTools {

		@McpTool(name = "search-users", description = "Search for users")
		fun search(@McpToolParam(description = "Search criteria") request: SearchRequest): String {
			return "${request.userId}:${request.username}:${request.limit}"
		}

	}

}
