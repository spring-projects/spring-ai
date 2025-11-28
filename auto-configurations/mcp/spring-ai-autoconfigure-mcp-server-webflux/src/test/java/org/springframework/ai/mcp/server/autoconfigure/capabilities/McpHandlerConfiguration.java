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

package org.springframework.ai.mcp.server.autoconfigure.capabilities;

import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpElicitation;
import org.springaicommunity.mcp.context.StructuredElicitResult;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.web.context.annotation.RequestScope;

@Configuration
public class McpHandlerConfiguration {

	private static final Logger logger = LoggerFactory.getLogger(McpHandlerConfiguration.class);

	@Bean
	ElicitationHandler elicitationHandler() {
		return new ElicitationHandler();
	}

	// Ensure that we don't blow up on non-singleton beans
	@Bean
	@Scope(scopeName = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	Foo foo() {
		return new Foo();
	}

	// Ensure that we don't blow up on non-singleton beans
	@Bean
	@RequestScope
	Bar bar(Foo foo) {
		return new Bar();
	}

	record ElicitationHandler() {

		@McpElicitation(clients = "server1")
		public StructuredElicitResult<ElicitInput> elicitationHandler(McpSchema.ElicitRequest request) {
			logger.info("MCP ELICITATION: {}", request);
			ElicitInput elicitData = new ElicitInput(request.message());
			return StructuredElicitResult.builder().structuredContent(elicitData).build();
		}

	}

	public record ElicitInput(String message) {
	}

	public static class Foo {

	}

	public static class Bar {

	}

}
