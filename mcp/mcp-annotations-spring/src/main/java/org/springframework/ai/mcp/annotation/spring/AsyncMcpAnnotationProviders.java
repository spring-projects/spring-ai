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
import java.util.List;

import io.modelcontextprotocol.server.McpServerFeatures.AsyncCompletionSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.AsyncPromptSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.AsyncResourceSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import org.springaicommunity.mcp.method.changed.prompt.AsyncPromptListChangedSpecification;
import org.springaicommunity.mcp.method.changed.resource.AsyncResourceListChangedSpecification;
import org.springaicommunity.mcp.method.changed.tool.AsyncToolListChangedSpecification;
import org.springaicommunity.mcp.method.elicitation.AsyncElicitationSpecification;
import org.springaicommunity.mcp.method.logging.AsyncLoggingSpecification;
import org.springaicommunity.mcp.method.progress.AsyncProgressSpecification;
import org.springaicommunity.mcp.method.sampling.AsyncSamplingSpecification;
import org.springaicommunity.mcp.provider.changed.prompt.AsyncMcpPromptListChangedProvider;
import org.springaicommunity.mcp.provider.changed.resource.AsyncMcpResourceListChangedProvider;
import org.springaicommunity.mcp.provider.changed.tool.AsyncMcpToolListChangedProvider;
import org.springaicommunity.mcp.provider.complete.AsyncMcpCompleteProvider;
import org.springaicommunity.mcp.provider.complete.AsyncStatelessMcpCompleteProvider;
import org.springaicommunity.mcp.provider.elicitation.AsyncMcpElicitationProvider;
import org.springaicommunity.mcp.provider.logging.AsyncMcpLoggingProvider;
import org.springaicommunity.mcp.provider.progress.AsyncMcpProgressProvider;
import org.springaicommunity.mcp.provider.prompt.AsyncMcpPromptProvider;
import org.springaicommunity.mcp.provider.prompt.AsyncStatelessMcpPromptProvider;
import org.springaicommunity.mcp.provider.resource.AsyncMcpResourceProvider;
import org.springaicommunity.mcp.provider.resource.AsyncStatelessMcpResourceProvider;
import org.springaicommunity.mcp.provider.sampling.AsyncMcpSamplingProvider;
import org.springaicommunity.mcp.provider.tool.AsyncMcpToolProvider;
import org.springaicommunity.mcp.provider.tool.AsyncStatelessMcpToolProvider;

/**
 * @author Christian Tzolov
 */
public final class AsyncMcpAnnotationProviders {

	private AsyncMcpAnnotationProviders() {
	}

	//
	// UTILITIES
	//

	// LOGGING (CLIENT)
	public static List<AsyncLoggingSpecification> loggingSpecifications(List<Object> loggingObjects) {
		return new SpringAiAsyncMcpLoggingProvider(loggingObjects).getLoggingSpecifications();
	}

	// SAMPLING (CLIENT)
	public static List<AsyncSamplingSpecification> samplingSpecifications(List<Object> samplingObjects) {
		return new SpringAiAsyncMcpSamplingProvider(samplingObjects).getSamplingSpecifictions();
	}

	// ELICITATION (CLIENT)
	public static List<AsyncElicitationSpecification> elicitationSpecifications(List<Object> elicitationObjects) {
		return new SpringAiAsyncMcpElicitationProvider(elicitationObjects).getElicitationSpecifications();
	}

	// PROGRESS (CLIENT)
	public static List<AsyncProgressSpecification> progressSpecifications(List<Object> progressObjects) {
		return new SpringAiAsyncMcpProgressProvider(progressObjects).getProgressSpecifications();
	}

	// TOOL
	public static List<AsyncToolSpecification> toolSpecifications(List<Object> toolObjects) {
		return new SpringAiAsyncMcpToolProvider(toolObjects).getToolSpecifications();
	}

	public static List<McpStatelessServerFeatures.AsyncToolSpecification> statelessToolSpecifications(
			List<Object> toolObjects) {
		return new SpringAiAsyncStatelessMcpToolProvider(toolObjects).getToolSpecifications();
	}

	// COMPLETE
	public static List<AsyncCompletionSpecification> completeSpecifications(List<Object> completeObjects) {
		return new SpringAiAsyncMcpCompleteProvider(completeObjects).getCompleteSpecifications();
	}

	public static List<McpStatelessServerFeatures.AsyncCompletionSpecification> statelessCompleteSpecifications(
			List<Object> completeObjects) {
		return new SpringAiAsyncStatelessMcpCompleteProvider(completeObjects).getCompleteSpecifications();
	}

	// PROMPT
	public static List<AsyncPromptSpecification> promptSpecifications(List<Object> promptObjects) {
		return new SpringAiAsyncPromptProvider(promptObjects).getPromptSpecifications();
	}

	public static List<McpStatelessServerFeatures.AsyncPromptSpecification> statelessPromptSpecifications(
			List<Object> promptObjects) {
		return new SpringAiAsyncStatelessPromptProvider(promptObjects).getPromptSpecifications();
	}

	// RESOURCE
	public static List<AsyncResourceSpecification> resourceSpecifications(List<Object> resourceObjects) {
		return new SpringAiAsyncResourceProvider(resourceObjects).getResourceSpecifications();
	}

	public static List<McpStatelessServerFeatures.AsyncResourceSpecification> statelessResourceSpecifications(
			List<Object> resourceObjects) {
		return new SpringAiAsyncStatelessResourceProvider(resourceObjects).getResourceSpecifications();
	}

	// RESOURCE LIST CHANGED
	public static List<AsyncResourceListChangedSpecification> resourceListChangedSpecifications(
			List<Object> resourceListChangedObjects) {
		return new SpringAiAsyncMcpResourceListChangedProvider(resourceListChangedObjects)
			.getResourceListChangedSpecifications();
	}

	// TOOL LIST CHANGED
	public static List<AsyncToolListChangedSpecification> toolListChangedSpecifications(
			List<Object> toolListChangedObjects) {
		return new SpringAiAsyncMcpToolListChangedProvider(toolListChangedObjects).getToolListChangedSpecifications();
	}

	// PROMPT LIST CHANGED
	public static List<AsyncPromptListChangedSpecification> promptListChangedSpecifications(
			List<Object> promptListChangedObjects) {
		return new SpringAiAsyncMcpPromptListChangedProvider(promptListChangedObjects)
			.getPromptListChangedSpecifications();
	}

	// LOGGING (CLIENT)
	private final static class SpringAiAsyncMcpLoggingProvider extends AsyncMcpLoggingProvider {

		private SpringAiAsyncMcpLoggingProvider(List<Object> loggingObjects) {
			super(loggingObjects);
		}

		@Override
		protected Method[] doGetClassMethods(Object bean) {
			return AnnotationProviderUtil.beanMethods(bean);
		}

	}

	// SAMPLING (CLIENT)
	private final static class SpringAiAsyncMcpSamplingProvider extends AsyncMcpSamplingProvider {

		private SpringAiAsyncMcpSamplingProvider(List<Object> samplingObjects) {
			super(samplingObjects);
		}

		@Override
		protected Method[] doGetClassMethods(Object bean) {
			return AnnotationProviderUtil.beanMethods(bean);
		}

	}

	// ELICITATION (CLIENT)
	private final static class SpringAiAsyncMcpElicitationProvider extends AsyncMcpElicitationProvider {

		private SpringAiAsyncMcpElicitationProvider(List<Object> elicitationObjects) {
			super(elicitationObjects);
		}

		@Override
		protected Method[] doGetClassMethods(Object bean) {
			return AnnotationProviderUtil.beanMethods(bean);
		}

	}

	// PROGRESS (CLIENT)
	private final static class SpringAiAsyncMcpProgressProvider extends AsyncMcpProgressProvider {

		private SpringAiAsyncMcpProgressProvider(List<Object> progressObjects) {
			super(progressObjects);
		}

		@Override
		protected Method[] doGetClassMethods(Object bean) {
			return AnnotationProviderUtil.beanMethods(bean);
		}

	}

	// TOOL
	private final static class SpringAiAsyncMcpToolProvider extends AsyncMcpToolProvider {

		private SpringAiAsyncMcpToolProvider(List<Object> toolObjects) {
			super(toolObjects);
		}

		@Override
		protected Method[] doGetClassMethods(Object bean) {
			return AnnotationProviderUtil.beanMethods(bean);
		}

	}

	private final static class SpringAiAsyncStatelessMcpToolProvider extends AsyncStatelessMcpToolProvider {

		private SpringAiAsyncStatelessMcpToolProvider(List<Object> toolObjects) {
			super(toolObjects);
		}

		@Override
		protected Method[] doGetClassMethods(Object bean) {
			return AnnotationProviderUtil.beanMethods(bean);
		}

	}

	// COMPLETE
	private final static class SpringAiAsyncMcpCompleteProvider extends AsyncMcpCompleteProvider {

		private SpringAiAsyncMcpCompleteProvider(List<Object> completeObjects) {
			super(completeObjects);
		}

		@Override
		protected Method[] doGetClassMethods(Object bean) {
			return AnnotationProviderUtil.beanMethods(bean);
		}

	};

	private final static class SpringAiAsyncStatelessMcpCompleteProvider extends AsyncStatelessMcpCompleteProvider {

		private SpringAiAsyncStatelessMcpCompleteProvider(List<Object> completeObjects) {
			super(completeObjects);
		}

		@Override
		protected Method[] doGetClassMethods(Object bean) {
			return AnnotationProviderUtil.beanMethods(bean);
		}

	};

	// PROMPT
	private final static class SpringAiAsyncPromptProvider extends AsyncMcpPromptProvider {

		private SpringAiAsyncPromptProvider(List<Object> promptObjects) {
			super(promptObjects);
		}

		@Override
		protected Method[] doGetClassMethods(Object bean) {
			return AnnotationProviderUtil.beanMethods(bean);
		}

	}

	private final static class SpringAiAsyncStatelessPromptProvider extends AsyncStatelessMcpPromptProvider {

		private SpringAiAsyncStatelessPromptProvider(List<Object> promptObjects) {
			super(promptObjects);
		}

		@Override
		protected Method[] doGetClassMethods(Object bean) {
			return AnnotationProviderUtil.beanMethods(bean);
		}

	}

	// RESOURCE
	private final static class SpringAiAsyncResourceProvider extends AsyncMcpResourceProvider {

		private SpringAiAsyncResourceProvider(List<Object> resourceObjects) {
			super(resourceObjects);
		}

		@Override
		protected Method[] doGetClassMethods(Object bean) {
			return AnnotationProviderUtil.beanMethods(bean);
		}

	}

	private final static class SpringAiAsyncStatelessResourceProvider extends AsyncStatelessMcpResourceProvider {

		private SpringAiAsyncStatelessResourceProvider(List<Object> resourceObjects) {
			super(resourceObjects);
		}

		@Override
		protected Method[] doGetClassMethods(Object bean) {
			return AnnotationProviderUtil.beanMethods(bean);
		}

	}

	// TOOL LIST CHANGED
	private final static class SpringAiAsyncMcpToolListChangedProvider extends AsyncMcpToolListChangedProvider {

		private SpringAiAsyncMcpToolListChangedProvider(List<Object> toolListChangedObjects) {
			super(toolListChangedObjects);
		}

		@Override
		protected Method[] doGetClassMethods(Object bean) {
			return AnnotationProviderUtil.beanMethods(bean);
		}

	}

	// RESOURCE LIST CHANGED
	private final static class SpringAiAsyncMcpResourceListChangedProvider extends AsyncMcpResourceListChangedProvider {

		private SpringAiAsyncMcpResourceListChangedProvider(List<Object> resourceListChangedObjects) {
			super(resourceListChangedObjects);
		}

		@Override
		protected Method[] doGetClassMethods(Object bean) {
			return AnnotationProviderUtil.beanMethods(bean);
		}

	}

	// PROMPT LIST CHANGED
	private final static class SpringAiAsyncMcpPromptListChangedProvider extends AsyncMcpPromptListChangedProvider {

		private SpringAiAsyncMcpPromptListChangedProvider(List<Object> promptListChangedObjects) {
			super(promptListChangedObjects);
		}

		@Override
		protected Method[] doGetClassMethods(Object bean) {
			return AnnotationProviderUtil.beanMethods(bean);
		}

	}

}
