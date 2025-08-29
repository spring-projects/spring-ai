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

package org.springframework.ai.mcp.annotation.spring;

import java.util.ArrayList;
import java.util.List;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springaicommunity.mcp.method.changed.prompt.AsyncPromptListChangedSpecification;
import org.springaicommunity.mcp.method.changed.resource.AsyncResourceListChangedSpecification;
import org.springaicommunity.mcp.method.changed.tool.AsyncToolListChangedSpecification;
import org.springaicommunity.mcp.method.elicitation.AsyncElicitationSpecification;
import org.springaicommunity.mcp.method.logging.AsyncLoggingSpecification;
import org.springaicommunity.mcp.method.progress.AsyncProgressSpecification;
import org.springaicommunity.mcp.method.sampling.AsyncSamplingSpecification;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit Tests for {@link AsyncMcpAnnotationProviders}.
 *
 * @author Sun Yuhan
 */
@ExtendWith(MockitoExtension.class)
class AsyncMcpAnnotationProvidersTests {

	@Test
	void testLoggingSpecifications() {
		List<Object> loggingObjects = new ArrayList<>();
		loggingObjects.add(new Object());

		List<AsyncLoggingSpecification> result = AsyncMcpAnnotationProviders.loggingSpecifications(loggingObjects);

		assertNotNull(result);
	}

	@Test
	void testLoggingSpecificationsWithEmptyList() {
		List<Object> loggingObjects = new ArrayList<>();

		List<AsyncLoggingSpecification> result = AsyncMcpAnnotationProviders.loggingSpecifications(loggingObjects);

		assertNotNull(result);
		assertTrue(result.isEmpty());
	}

	@Test
	void testSamplingSpecifications() {
		List<Object> samplingObjects = new ArrayList<>();
		samplingObjects.add(new Object());

		List<AsyncSamplingSpecification> result = AsyncMcpAnnotationProviders.samplingSpecifications(samplingObjects);

		assertNotNull(result);
	}

	@Test
	void testElicitationSpecifications() {
		List<Object> elicitationObjects = new ArrayList<>();
		elicitationObjects.add(new Object());

		List<AsyncElicitationSpecification> result = AsyncMcpAnnotationProviders
			.elicitationSpecifications(elicitationObjects);

		assertNotNull(result);
	}

	@Test
	void testProgressSpecifications() {
		List<Object> progressObjects = new ArrayList<>();
		progressObjects.add(new Object());

		List<AsyncProgressSpecification> result = AsyncMcpAnnotationProviders.progressSpecifications(progressObjects);

		assertNotNull(result);
	}

	@Test
	void testToolSpecifications() {
		List<Object> toolObjects = new ArrayList<>();
		toolObjects.add(new Object());

		List<McpServerFeatures.AsyncToolSpecification> result = AsyncMcpAnnotationProviders
			.toolSpecifications(toolObjects);

		assertNotNull(result);
	}

	@Test
	void testStatelessToolSpecifications() {

		List<Object> toolObjects = new ArrayList<>();
		toolObjects.add(new Object());

		List<McpStatelessServerFeatures.AsyncToolSpecification> result = AsyncMcpAnnotationProviders
			.statelessToolSpecifications(toolObjects);

		assertNotNull(result);
	}

	@Test
	void testCompleteSpecifications() {
		List<Object> completeObjects = new ArrayList<>();
		completeObjects.add(new Object());

		List<McpServerFeatures.AsyncCompletionSpecification> result = AsyncMcpAnnotationProviders
			.completeSpecifications(completeObjects);

		assertNotNull(result);
	}

	@Test
	void testStatelessCompleteSpecifications() {
		List<Object> completeObjects = new ArrayList<>();
		completeObjects.add(new Object());

		List<McpStatelessServerFeatures.AsyncCompletionSpecification> result = AsyncMcpAnnotationProviders
			.statelessCompleteSpecifications(completeObjects);

		assertNotNull(result);
	}

	@Test
	void testPromptSpecifications() {
		List<Object> promptObjects = new ArrayList<>();
		promptObjects.add(new Object());

		List<McpServerFeatures.AsyncPromptSpecification> result = AsyncMcpAnnotationProviders
			.promptSpecifications(promptObjects);

		assertNotNull(result);
	}

	@Test
	void testStatelessPromptSpecifications() {
		List<Object> promptObjects = new ArrayList<>();
		promptObjects.add(new Object());

		List<McpStatelessServerFeatures.AsyncPromptSpecification> result = AsyncMcpAnnotationProviders
			.statelessPromptSpecifications(promptObjects);

		assertNotNull(result);
	}

	@Test
	void testResourceSpecifications() {
		List<Object> resourceObjects = new ArrayList<>();
		resourceObjects.add(new Object());

		List<McpServerFeatures.AsyncResourceSpecification> result = AsyncMcpAnnotationProviders
			.resourceSpecifications(resourceObjects);

		assertNotNull(result);
	}

	@Test
	void testStatelessResourceSpecifications() {
		List<Object> resourceObjects = new ArrayList<>();
		resourceObjects.add(new Object());

		List<McpStatelessServerFeatures.AsyncResourceSpecification> result = AsyncMcpAnnotationProviders
			.statelessResourceSpecifications(resourceObjects);

		assertNotNull(result);
	}

	@Test
	void testResourceListChangedSpecifications() {
		List<Object> resourceListChangedObjects = new ArrayList<>();
		resourceListChangedObjects.add(new Object());

		List<AsyncResourceListChangedSpecification> result = AsyncMcpAnnotationProviders
			.resourceListChangedSpecifications(resourceListChangedObjects);

		assertNotNull(result);
	}

	@Test
	void testToolListChangedSpecifications() {
		List<Object> toolListChangedObjects = new ArrayList<>();
		toolListChangedObjects.add(new Object());

		List<AsyncToolListChangedSpecification> result = AsyncMcpAnnotationProviders
			.toolListChangedSpecifications(toolListChangedObjects);

		assertNotNull(result);
	}

	@Test
	void testPromptListChangedSpecifications() {
		List<Object> promptListChangedObjects = new ArrayList<>();
		promptListChangedObjects.add(new Object());

		List<AsyncPromptListChangedSpecification> result = AsyncMcpAnnotationProviders
			.promptListChangedSpecifications(promptListChangedObjects);

		assertNotNull(result);
	}

}
