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

package org.springframework.ai.mcp.annotation.spring;

import java.lang.reflect.Method;
import java.util.List;

import io.modelcontextprotocol.server.McpServerFeatures.SyncCompletionSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncPromptSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncResourceSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncResourceTemplateSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;

import org.springframework.ai.mcp.annotation.method.changed.prompt.SyncPromptListChangedSpecification;
import org.springframework.ai.mcp.annotation.method.changed.resource.SyncResourceListChangedSpecification;
import org.springframework.ai.mcp.annotation.method.changed.tool.SyncToolListChangedSpecification;
import org.springframework.ai.mcp.annotation.method.elicitation.SyncElicitationSpecification;
import org.springframework.ai.mcp.annotation.method.logging.SyncLoggingSpecification;
import org.springframework.ai.mcp.annotation.method.progress.SyncProgressSpecification;
import org.springframework.ai.mcp.annotation.method.sampling.SyncSamplingSpecification;
import org.springframework.ai.mcp.annotation.provider.changed.prompt.SyncMcpPromptListChangedProvider;
import org.springframework.ai.mcp.annotation.provider.changed.resource.SyncMcpResourceListChangedProvider;
import org.springframework.ai.mcp.annotation.provider.changed.tool.SyncMcpToolListChangedProvider;
import org.springframework.ai.mcp.annotation.provider.complete.SyncMcpCompleteProvider;
import org.springframework.ai.mcp.annotation.provider.complete.SyncStatelessMcpCompleteProvider;
import org.springframework.ai.mcp.annotation.provider.elicitation.SyncMcpElicitationProvider;
import org.springframework.ai.mcp.annotation.provider.logging.SyncMcpLoggingProvider;
import org.springframework.ai.mcp.annotation.provider.progress.SyncMcpProgressProvider;
import org.springframework.ai.mcp.annotation.provider.prompt.SyncMcpPromptProvider;
import org.springframework.ai.mcp.annotation.provider.prompt.SyncStatelessMcpPromptProvider;
import org.springframework.ai.mcp.annotation.provider.resource.SyncMcpResourceProvider;
import org.springframework.ai.mcp.annotation.provider.resource.SyncStatelessMcpResourceProvider;
import org.springframework.ai.mcp.annotation.provider.sampling.SyncMcpSamplingProvider;
import org.springframework.ai.mcp.annotation.provider.tool.SyncMcpToolProvider;
import org.springframework.ai.mcp.annotation.provider.tool.SyncStatelessMcpToolProvider;

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

	// RESOURCE TEMPLATE
	public static List<SyncResourceTemplateSpecification> resourceTemplateSpecifications(List<Object> resourceObjects) {
		return new SpringAiSyncMcpResourceProvider(resourceObjects).getResourceTemplateSpecifications();
	}

	public static List<McpStatelessServerFeatures.SyncResourceTemplateSpecification> statelessResourceTemplateSpecifications(
			List<Object> resourceObjects) {
		return new SpringAiSyncStatelessResourceProvider(resourceObjects).getResourceTemplateSpecifications();
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

	}

	private final static class SpringAiSyncStatelessMcpCompleteProvider extends SyncStatelessMcpCompleteProvider {

		private SpringAiSyncStatelessMcpCompleteProvider(List<Object> completeObjects) {
			super(completeObjects);
		}

		@Override
		protected Method[] doGetClassMethods(Object bean) {
			return AnnotationProviderUtil.beanMethods(bean);
		}

	}

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

	}

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
	private final static class SpringAiSyncMcpLoggingProvider extends SyncMcpLoggingProvider {

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
