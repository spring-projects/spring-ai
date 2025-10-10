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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springaicommunity.mcp.method.changed.prompt.SyncPromptListChangedSpecification;
import org.springaicommunity.mcp.method.changed.resource.SyncResourceListChangedSpecification;
import org.springaicommunity.mcp.method.changed.tool.SyncToolListChangedSpecification;
import org.springaicommunity.mcp.method.elicitation.SyncElicitationSpecification;
import org.springaicommunity.mcp.method.logging.SyncLoggingSpecification;
import org.springaicommunity.mcp.method.progress.SyncProgressSpecification;
import org.springaicommunity.mcp.method.sampling.SyncSamplingSpecification;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

/**
 * Unit Tests for {@link SyncMcpAnnotationProviders}.
 *
 * @author Sun Yuhan
 */

@ExtendWith(MockitoExtension.class)
class SyncMcpAnnotationProvidersTests {

	@Test
	void testToolSpecificationsWithValidObjectsReturnsSpecifications() {
		List<Object> toolObjects = new ArrayList<>();
		toolObjects.add(new Object());

		try (MockedStatic<AnnotationProviderUtil> mockedUtil = mockStatic(AnnotationProviderUtil.class)) {
			mockedUtil.when(() -> AnnotationProviderUtil.beanMethods(any())).thenReturn(new Method[0]);

			List<McpServerFeatures.SyncToolSpecification> result = SyncMcpAnnotationProviders
				.toolSpecifications(toolObjects);

			assertNotNull(result);
		}
	}

	@Test
	void testToolSpecificationsWithEmptyListReturnsEmptyList() {
		List<Object> toolObjects = new ArrayList<>();

		List<McpServerFeatures.SyncToolSpecification> result = SyncMcpAnnotationProviders
			.toolSpecifications(toolObjects);

		assertNotNull(result);
		assertTrue(result.isEmpty());
	}

	@Test
	void testStatelessToolSpecificationsWithValidObjectsReturnsSpecifications() {
		List<Object> toolObjects = new ArrayList<>();
		toolObjects.add(new Object());

		try (MockedStatic<AnnotationProviderUtil> mockedUtil = mockStatic(AnnotationProviderUtil.class)) {
			mockedUtil.when(() -> AnnotationProviderUtil.beanMethods(any())).thenReturn(new Method[0]);

			List<McpStatelessServerFeatures.SyncToolSpecification> result = SyncMcpAnnotationProviders
				.statelessToolSpecifications(toolObjects);

			assertNotNull(result);
		}
	}

	@Test
	void testStatelessToolSpecificationsWithEmptyListReturnsEmptyList() {
		List<Object> toolObjects = new ArrayList<>();

		List<McpStatelessServerFeatures.SyncToolSpecification> result = SyncMcpAnnotationProviders
			.statelessToolSpecifications(toolObjects);

		assertNotNull(result);
		assertTrue(result.isEmpty());
	}

	@Test
	void testCompleteSpecificationsWithValidObjectsReturnsSpecifications() {
		List<Object> completeObjects = new ArrayList<>();
		completeObjects.add(new Object());

		try (MockedStatic<AnnotationProviderUtil> mockedUtil = mockStatic(AnnotationProviderUtil.class)) {
			mockedUtil.when(() -> AnnotationProviderUtil.beanMethods(any())).thenReturn(new Method[0]);

			List<McpServerFeatures.SyncCompletionSpecification> result = SyncMcpAnnotationProviders
				.completeSpecifications(completeObjects);

			assertNotNull(result);
		}
	}

	@Test
	void testStatelessCompleteSpecificationsWithValidObjectsReturnsSpecifications() {
		List<Object> completeObjects = new ArrayList<>();
		completeObjects.add(new Object());

		try (MockedStatic<AnnotationProviderUtil> mockedUtil = mockStatic(AnnotationProviderUtil.class)) {
			mockedUtil.when(() -> AnnotationProviderUtil.beanMethods(any())).thenReturn(new Method[0]);

			List<McpStatelessServerFeatures.SyncCompletionSpecification> result = SyncMcpAnnotationProviders
				.statelessCompleteSpecifications(completeObjects);

			assertNotNull(result);
		}
	}

	@Test
	void testPromptSpecificationsWithValidObjectsReturnsSpecifications() {
		List<Object> promptObjects = new ArrayList<>();
		promptObjects.add(new Object());

		try (MockedStatic<AnnotationProviderUtil> mockedUtil = mockStatic(AnnotationProviderUtil.class)) {
			mockedUtil.when(() -> AnnotationProviderUtil.beanMethods(any())).thenReturn(new Method[0]);

			List<McpServerFeatures.SyncPromptSpecification> result = SyncMcpAnnotationProviders
				.promptSpecifications(promptObjects);

			assertNotNull(result);
		}
	}

	@Test
	void testStatelessPromptSpecificationsWithValidObjectsReturnsSpecifications() {
		List<Object> promptObjects = new ArrayList<>();
		promptObjects.add(new Object());

		try (MockedStatic<AnnotationProviderUtil> mockedUtil = mockStatic(AnnotationProviderUtil.class)) {
			mockedUtil.when(() -> AnnotationProviderUtil.beanMethods(any())).thenReturn(new Method[0]);

			List<McpStatelessServerFeatures.SyncPromptSpecification> result = SyncMcpAnnotationProviders
				.statelessPromptSpecifications(promptObjects);

			assertNotNull(result);
		}
	}

	@Test
	void testResourceSpecificationsWithValidObjectsReturnsSpecifications() {
		List<Object> resourceObjects = new ArrayList<>();
		resourceObjects.add(new Object());

		try (MockedStatic<AnnotationProviderUtil> mockedUtil = mockStatic(AnnotationProviderUtil.class)) {
			mockedUtil.when(() -> AnnotationProviderUtil.beanMethods(any())).thenReturn(new Method[0]);

			List<McpServerFeatures.SyncResourceSpecification> result = SyncMcpAnnotationProviders
				.resourceSpecifications(resourceObjects);

			assertNotNull(result);
		}
	}

	@Test
	void testStatelessResourceSpecificationsWithValidObjectsReturnsSpecifications() {
		List<Object> resourceObjects = new ArrayList<>();
		resourceObjects.add(new Object());

		try (MockedStatic<AnnotationProviderUtil> mockedUtil = mockStatic(AnnotationProviderUtil.class)) {
			mockedUtil.when(() -> AnnotationProviderUtil.beanMethods(any())).thenReturn(new Method[0]);

			List<McpStatelessServerFeatures.SyncResourceSpecification> result = SyncMcpAnnotationProviders
				.statelessResourceSpecifications(resourceObjects);

			assertNotNull(result);
		}
	}

	@Test
	void testLoggingSpecificationsWithValidObjectsReturnsSpecifications() {
		List<Object> loggingObjects = new ArrayList<>();
		loggingObjects.add(new Object());

		try (MockedStatic<AnnotationProviderUtil> mockedUtil = mockStatic(AnnotationProviderUtil.class)) {
			mockedUtil.when(() -> AnnotationProviderUtil.beanMethods(any())).thenReturn(new Method[0]);

			List<SyncLoggingSpecification> result = SyncMcpAnnotationProviders.loggingSpecifications(loggingObjects);

			assertNotNull(result);
		}
	}

	@Test
	void testSamplingSpecificationsWithValidObjectsReturnsSpecifications() {
		List<Object> samplingObjects = new ArrayList<>();
		samplingObjects.add(new Object());

		try (MockedStatic<AnnotationProviderUtil> mockedUtil = mockStatic(AnnotationProviderUtil.class)) {
			mockedUtil.when(() -> AnnotationProviderUtil.beanMethods(any())).thenReturn(new Method[0]);

			List<SyncSamplingSpecification> result = SyncMcpAnnotationProviders.samplingSpecifications(samplingObjects);

			assertNotNull(result);
		}
	}

	@Test
	void testElicitationSpecificationsWithValidObjectsReturnsSpecifications() {
		List<Object> elicitationObjects = new ArrayList<>();
		elicitationObjects.add(new Object());

		try (MockedStatic<AnnotationProviderUtil> mockedUtil = mockStatic(AnnotationProviderUtil.class)) {
			mockedUtil.when(() -> AnnotationProviderUtil.beanMethods(any())).thenReturn(new Method[0]);

			List<SyncElicitationSpecification> result = SyncMcpAnnotationProviders
				.elicitationSpecifications(elicitationObjects);

			assertNotNull(result);
		}
	}

	@Test
	void testProgressSpecificationsWithValidObjectsReturnsSpecifications() {
		List<Object> progressObjects = new ArrayList<>();
		progressObjects.add(new Object());

		try (MockedStatic<AnnotationProviderUtil> mockedUtil = mockStatic(AnnotationProviderUtil.class)) {
			mockedUtil.when(() -> AnnotationProviderUtil.beanMethods(any())).thenReturn(new Method[0]);

			List<SyncProgressSpecification> result = SyncMcpAnnotationProviders.progressSpecifications(progressObjects);

			assertNotNull(result);
		}
	}

	@Test
	void testToolListChangedSpecificationsWithValidObjectsReturnsSpecifications() {
		List<Object> toolListChangedObjects = new ArrayList<>();
		toolListChangedObjects.add(new Object());

		try (MockedStatic<AnnotationProviderUtil> mockedUtil = mockStatic(AnnotationProviderUtil.class)) {
			mockedUtil.when(() -> AnnotationProviderUtil.beanMethods(any())).thenReturn(new Method[0]);

			List<SyncToolListChangedSpecification> result = SyncMcpAnnotationProviders
				.toolListChangedSpecifications(toolListChangedObjects);

			assertNotNull(result);
		}
	}

	@Test
	void testResourceListChangedSpecificationsWithValidObjectsReturnsSpecifications() {
		List<Object> resourceListChangedObjects = new ArrayList<>();
		resourceListChangedObjects.add(new Object());

		try (MockedStatic<AnnotationProviderUtil> mockedUtil = mockStatic(AnnotationProviderUtil.class)) {
			mockedUtil.when(() -> AnnotationProviderUtil.beanMethods(any())).thenReturn(new Method[0]);

			List<SyncResourceListChangedSpecification> result = SyncMcpAnnotationProviders
				.resourceListChangedSpecifications(resourceListChangedObjects);

			assertNotNull(result);
		}
	}

	@Test
	void testPromptListChangedSpecificationsWithValidObjectsReturnsSpecifications() {
		List<Object> promptListChangedObjects = new ArrayList<>();
		promptListChangedObjects.add(new Object());

		try (MockedStatic<AnnotationProviderUtil> mockedUtil = mockStatic(AnnotationProviderUtil.class)) {
			mockedUtil.when(() -> AnnotationProviderUtil.beanMethods(any())).thenReturn(new Method[0]);

			List<SyncPromptListChangedSpecification> result = SyncMcpAnnotationProviders
				.promptListChangedSpecifications(promptListChangedObjects);

			assertNotNull(result);
		}
	}

}
