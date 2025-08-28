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

import io.modelcontextprotocol.server.McpServerFeatures.SyncCompletionSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncPromptSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncResourceSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import org.springaicommunity.mcp.method.changed.prompt.SyncPromptListChangedSpecification;
import org.springaicommunity.mcp.method.changed.resource.SyncResourceListChangedSpecification;
import org.springaicommunity.mcp.method.changed.tool.SyncToolListChangedSpecification;
import org.springaicommunity.mcp.method.elicitation.SyncElicitationSpecification;
import org.springaicommunity.mcp.method.logging.SyncLoggingSpecification;
import org.springaicommunity.mcp.method.progress.SyncProgressSpecification;
import org.springaicommunity.mcp.method.sampling.SyncSamplingSpecification;
import org.springaicommunity.mcp.provider.changed.prompt.SyncMcpPromptListChangedProvider;
import org.springaicommunity.mcp.provider.changed.resource.SyncMcpResourceListChangedProvider;
import org.springaicommunity.mcp.provider.changed.tool.SyncMcpToolListChangedProvider;
import org.springaicommunity.mcp.provider.complete.SyncMcpCompleteProvider;
import org.springaicommunity.mcp.provider.complete.SyncStatelessMcpCompleteProvider;
import org.springaicommunity.mcp.provider.elicitation.SyncMcpElicitationProvider;
import org.springaicommunity.mcp.provider.logging.SyncMcpLogginProvider;
import org.springaicommunity.mcp.provider.progress.SyncMcpProgressProvider;
import org.springaicommunity.mcp.provider.prompt.SyncMcpPromptProvider;
import org.springaicommunity.mcp.provider.prompt.SyncStatelessMcpPromptProvider;
import org.springaicommunity.mcp.provider.resource.SyncMcpResourceProvider;
import org.springaicommunity.mcp.provider.resource.SyncStatelessMcpResourceProvider;
import org.springaicommunity.mcp.provider.sampling.SyncMcpSamplingProvider;
import org.springaicommunity.mcp.provider.tool.SyncMcpToolProvider;
import org.springaicommunity.mcp.provider.tool.SyncStatelessMcpToolProvider;

/**
 * @author Christian Tzolov
 */
public final class SyncMcpAnnotationProviders {

	private SyncMcpAnnotationProviders() {
	}

	//
	// UTILITIES
	//

	// TOOLS
	public static List<SyncToolSpecification> toolSpecifications(List<Object> toolObjects) {
		return new SpringAiSyncToolProvider(toolObjects).getToolSpecifications();
	}

	public static List<McpStatelessServerFeatures.SyncToolSpecification> statelessToolSpecifications(
			List<Object> toolObjects) {
		return new SpringAiSyncStatelessToolProvider(toolObjects).getToolSpecifications();
	}

	// COMPLETE
	public static List<SyncCompletionSpecification> completeSpecifications(List<Object> completeObjects) {
		return new SpringAiSyncMcpCompleteProvider(completeObjects).getCompleteSpecifications();
	}

	public static List<McpStatelessServerFeatures.SyncCompletionSpecification> statelessCompleteSpecifications(
			List<Object> completeObjects) {
		return new SpringAiSyncStatelessMcpCompleteProvider(completeObjects).getCompleteSpecifications();
	}

	// PROMPT
	public static List<SyncPromptSpecification> promptSpecifications(List<Object> promptObjects) {
		return new SpringAiSyncMcpPromptProvider(promptObjects).getPromptSpecifications();
	}

	public static List<McpStatelessServerFeatures.SyncPromptSpecification> statelessPromptSpecifications(
			List<Object> promptObjects) {
		return new SpringAiSyncStatelessPromptProvider(promptObjects).getPromptSpecifications();
	}

	// RESOURCE
	public static List<SyncResourceSpecification> resourceSpecifications(List<Object> resourceObjects) {
		return new SpringAiSyncMcpResourceProvider(resourceObjects).getResourceSpecifications();
	}

	public static List<McpStatelessServerFeatures.SyncResourceSpecification> statelessResourceSpecifications(
			List<Object> resourceObjects) {
		return new SpringAiSyncStatelessResourceProvider(resourceObjects).getResourceSpecifications();
	}

	// LOGGING (CLIENT)
	public static List<SyncLoggingSpecification> loggingSpecifications(List<Object> loggingObjects) {
		return new SpringAiSyncMcpLoggingProvider(loggingObjects).getLoggingSpecifications();
	}

	// SAMPLING (CLIENT)
	public static List<SyncSamplingSpecification> samplingSpecifications(List<Object> samplingObjects) {
		return new SpringAiSyncMcpSamplingProvider(samplingObjects).getSamplingSpecifications();
	}

	// ELICITATION (CLIENT)
	public static List<SyncElicitationSpecification> elicitationSpecifications(List<Object> elicitationObjects) {
		return new SpringAiSyncMcpElicitationProvider(elicitationObjects).getElicitationSpecifications();
	}

	// PROGRESS (CLIENT)
	public static List<SyncProgressSpecification> progressSpecifications(List<Object> progressObjects) {
		return new SpringAiSyncMcpProgressProvider(progressObjects).getProgressSpecifications();
	}

	// TOOL LIST CHANGED
	public static List<SyncToolListChangedSpecification> toolListChangedSpecifications(
			List<Object> toolListChangedObjects) {
		return new SpringAiSyncMcpToolListChangedProvider(toolListChangedObjects).getToolListChangedSpecifications();
	}

	// RESOURCE LIST CHANGED
	public static List<SyncResourceListChangedSpecification> resourceListChangedSpecifications(
			List<Object> resourceListChangedObjects) {
		return new SpringAiSyncMcpResourceListChangedProvider(resourceListChangedObjects)
			.getResourceListChangedSpecifications();
	}

	// PROMPT LIST CHANGED
	public static List<SyncPromptListChangedSpecification> promptListChangedSpecifications(
			List<Object> promptListChangedObjects) {
		return new SpringAiSyncMcpPromptListChangedProvider(promptListChangedObjects)
			.getPromptListChangedSpecifications();
	}

	// COMPLETE
	private final static class SpringAiSyncMcpCompleteProvider extends SyncMcpCompleteProvider {

		private SpringAiSyncMcpCompleteProvider(List<Object> completeObjects) {
			super(completeObjects);
		}

		@Override
		protected Method[] doGetClassMethods(Object bean) {
			return AnnotationProviderUtil.beanMethods(bean);
		}

	};

	private final static class SpringAiSyncStatelessMcpCompleteProvider extends SyncStatelessMcpCompleteProvider {

		private SpringAiSyncStatelessMcpCompleteProvider(List<Object> completeObjects) {
			super(completeObjects);
		}

		@Override
		protected Method[] doGetClassMethods(Object bean) {
			return AnnotationProviderUtil.beanMethods(bean);
		}

	};

	// TOOL
	private final static class SpringAiSyncToolProvider extends SyncMcpToolProvider {

		private SpringAiSyncToolProvider(List<Object> toolObjects) {
			super(toolObjects);
		}

		@Override
		protected Method[] doGetClassMethods(Object bean) {
			return AnnotationProviderUtil.beanMethods(bean);
		}

	}

	private final static class SpringAiSyncStatelessToolProvider extends SyncStatelessMcpToolProvider {

		private SpringAiSyncStatelessToolProvider(List<Object> toolObjects) {
			super(toolObjects);
		}

		@Override
		protected Method[] doGetClassMethods(Object bean) {
			return AnnotationProviderUtil.beanMethods(bean);
		}

	}

	// PROMPT
	private final static class SpringAiSyncMcpPromptProvider extends SyncMcpPromptProvider {

		private SpringAiSyncMcpPromptProvider(List<Object> promptObjects) {
			super(promptObjects);
		}

		@Override
		protected Method[] doGetClassMethods(Object bean) {
			return AnnotationProviderUtil.beanMethods(bean);
		}

	};

	private final static class SpringAiSyncStatelessPromptProvider extends SyncStatelessMcpPromptProvider {

		private SpringAiSyncStatelessPromptProvider(List<Object> promptObjects) {
			super(promptObjects);
		}

		@Override
		protected Method[] doGetClassMethods(Object bean) {
			return AnnotationProviderUtil.beanMethods(bean);
		}

	}

	// RESOURCE
	private final static class SpringAiSyncMcpResourceProvider extends SyncMcpResourceProvider {

		private SpringAiSyncMcpResourceProvider(List<Object> resourceObjects) {
			super(resourceObjects);
		}

		@Override
		protected Method[] doGetClassMethods(Object bean) {
			return AnnotationProviderUtil.beanMethods(bean);
		}

	}

	private final static class SpringAiSyncStatelessResourceProvider extends SyncStatelessMcpResourceProvider {

		private SpringAiSyncStatelessResourceProvider(List<Object> resourceObjects) {
			super(resourceObjects);
		}

		@Override
		protected Method[] doGetClassMethods(Object bean) {
			return AnnotationProviderUtil.beanMethods(bean);
		}

	}

	// LOGGING (CLIENT)
	private final static class SpringAiSyncMcpLoggingProvider extends SyncMcpLogginProvider {

		private SpringAiSyncMcpLoggingProvider(List<Object> loggingObjects) {
			super(loggingObjects);
		}

		@Override
		protected Method[] doGetClassMethods(Object bean) {
			return AnnotationProviderUtil.beanMethods(bean);
		}

	}

	// SAMPLING (CLIENT)
	private final static class SpringAiSyncMcpSamplingProvider extends SyncMcpSamplingProvider {

		private SpringAiSyncMcpSamplingProvider(List<Object> samplingObjects) {
			super(samplingObjects);
		}

		@Override
		protected Method[] doGetClassMethods(Object bean) {
			return AnnotationProviderUtil.beanMethods(bean);
		}

	}

	// ELICITATION (CLIENT)
	private final static class SpringAiSyncMcpElicitationProvider extends SyncMcpElicitationProvider {

		private SpringAiSyncMcpElicitationProvider(List<Object> elicitationObjects) {
			super(elicitationObjects);
		}

		@Override
		protected Method[] doGetClassMethods(Object bean) {
			return AnnotationProviderUtil.beanMethods(bean);
		}

	}

	// PROGRESS (CLIENT)
	private final static class SpringAiSyncMcpProgressProvider extends SyncMcpProgressProvider {

		private SpringAiSyncMcpProgressProvider(List<Object> progressObjects) {
			super(progressObjects);
		}

		@Override
		protected Method[] doGetClassMethods(Object bean) {
			return AnnotationProviderUtil.beanMethods(bean);
		}

	}

	// TOOL LIST CHANGE
	private final static class SpringAiSyncMcpToolListChangedProvider extends SyncMcpToolListChangedProvider {

		private SpringAiSyncMcpToolListChangedProvider(List<Object> toolListChangedObjects) {
			super(toolListChangedObjects);
		}

		@Override
		protected Method[] doGetClassMethods(Object bean) {
			return AnnotationProviderUtil.beanMethods(bean);
		}

	}

	// RESOURCE LIST CHANGE
	private final static class SpringAiSyncMcpResourceListChangedProvider extends SyncMcpResourceListChangedProvider {

		private SpringAiSyncMcpResourceListChangedProvider(List<Object> resourceListChangedObjects) {
			super(resourceListChangedObjects);
		}

		@Override
		protected Method[] doGetClassMethods(Object bean) {
			return AnnotationProviderUtil.beanMethods(bean);
		}

	}

	// PROMPT LIST CHANGE
	private final static class SpringAiSyncMcpPromptListChangedProvider extends SyncMcpPromptListChangedProvider {

		private SpringAiSyncMcpPromptListChangedProvider(List<Object> promptListChangedObjects) {
			super(promptListChangedObjects);
		}

		@Override
		protected Method[] doGetClassMethods(Object bean) {
			return AnnotationProviderUtil.beanMethods(bean);
		}

	}

}
