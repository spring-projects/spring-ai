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

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.modern.server.feature.McpAsyncPrompt;
import io.modelcontextprotocol.modern.server.feature.McpAsyncTool;
import io.modelcontextprotocol.server.McpServerFeatures.AsyncCompletionSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.AsyncPromptSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.AsyncResourceSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.AsyncResourceTemplateSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.mcp.annotation.method.changed.prompt.AsyncPromptListChangedSpecification;
import org.springframework.ai.mcp.annotation.method.changed.resource.AsyncResourceListChangedSpecification;
import org.springframework.ai.mcp.annotation.method.changed.tool.AsyncToolListChangedSpecification;
import org.springframework.ai.mcp.annotation.method.elicitation.AsyncElicitationSpecification;
import org.springframework.ai.mcp.annotation.method.logging.AsyncLoggingSpecification;
import org.springframework.ai.mcp.annotation.method.progress.AsyncProgressSpecification;
import org.springframework.ai.mcp.annotation.method.sampling.AsyncSamplingSpecification;
import org.springframework.ai.mcp.annotation.provider.changed.prompt.AsyncMcpPromptListChangedProvider;
import org.springframework.ai.mcp.annotation.provider.changed.resource.AsyncMcpResourceListChangedProvider;
import org.springframework.ai.mcp.annotation.provider.changed.tool.AsyncMcpToolListChangedProvider;
import org.springframework.ai.mcp.annotation.provider.complete.AsyncMcpCompleteProvider;
import org.springframework.ai.mcp.annotation.provider.complete.AsyncModernMcpCompleteProvider;
import org.springframework.ai.mcp.annotation.provider.complete.AsyncStatelessMcpCompleteProvider;
import org.springframework.ai.mcp.annotation.provider.elicitation.AsyncMcpElicitationProvider;
import org.springframework.ai.mcp.annotation.provider.logging.AsyncMcpLoggingProvider;
import org.springframework.ai.mcp.annotation.provider.progress.AsyncMcpProgressProvider;
import org.springframework.ai.mcp.annotation.provider.prompt.AsyncMcpPromptProvider;
import org.springframework.ai.mcp.annotation.provider.prompt.AsyncModernMcpPromptProvider;
import org.springframework.ai.mcp.annotation.provider.prompt.AsyncStatelessMcpPromptProvider;
import org.springframework.ai.mcp.annotation.provider.resource.AsyncMcpResourceProvider;
import org.springframework.ai.mcp.annotation.provider.resource.AsyncModernMcpResourceProvider;
import org.springframework.ai.mcp.annotation.provider.resource.AsyncStatelessMcpResourceProvider;
import org.springframework.ai.mcp.annotation.provider.sampling.AsyncMcpSamplingProvider;
import org.springframework.ai.mcp.annotation.provider.tool.AsyncMcpToolProvider;
import org.springframework.ai.mcp.annotation.provider.tool.AsyncModernMcpToolProvider;
import org.springframework.ai.mcp.annotation.provider.tool.AsyncStatelessMcpToolProvider;
import org.springframework.ai.mcp.modern.AsyncResourceRegistration;
import org.springframework.ai.mcp.modern.AsyncResourceTemplateRegistration;

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

	// MODERN (2026-07-28) SERVER
	/**
	 * @since 2.1.0
	 */
	public static List<McpAsyncTool> modernTools(List<Object> toolObjects) {
		return modernTools(toolObjects, null);
	}

	/**
	 * @param jsonMapper the server's mapper, used for the tool schemas and for converting
	 * MRTR input responses, or {@code null} for the SDK default
	 * @since 2.1.0
	 */
	public static List<McpAsyncTool> modernTools(List<Object> toolObjects, @Nullable McpJsonMapper jsonMapper) {
		SpringAiAsyncModernToolProvider provider = new SpringAiAsyncModernToolProvider(toolObjects);
		if (jsonMapper != null) {
			provider.setJsonMapper(jsonMapper);
		}
		return provider.getTools();
	}

	/**
	 * @since 2.1.0
	 */
	public static List<McpAsyncPrompt> modernPrompts(List<Object> promptObjects) {
		return modernPrompts(promptObjects, null);
	}

	/**
	 * @param jsonMapper the server's mapper, used for converting MRTR input responses, or
	 * {@code null} for the SDK default
	 * @since 2.1.0
	 */
	public static List<McpAsyncPrompt> modernPrompts(List<Object> promptObjects, @Nullable McpJsonMapper jsonMapper) {
		SpringAiAsyncModernPromptProvider provider = new SpringAiAsyncModernPromptProvider(promptObjects);
		provider.setJsonMapper(jsonMapper);
		return provider.getPrompts();
	}

	/**
	 * The prompts of the {@code @McpPrompt} methods, each with the completion handler of
	 * the {@code @McpComplete(prompt = ...)} method that names it.
	 * @param completeObjects the beans with {@code @McpComplete} methods
	 * @param jsonMapper the server's mapper, used for converting MRTR input responses, or
	 * {@code null} for the SDK default
	 * @throws IllegalStateException if a {@code @McpComplete} method names a prompt that
	 * no {@code @McpPrompt} method defines
	 * @since 2.1.0
	 */
	public static List<McpAsyncPrompt> modernPrompts(List<Object> promptObjects, List<Object> completeObjects,
			@Nullable McpJsonMapper jsonMapper) {
		return ModernCompletionJoin.prompts(modernPrompts(promptObjects, jsonMapper),
				new SpringAiAsyncModernCompleteProvider(completeObjects).getPromptCompletions(),
				prompt -> prompt.definition().name(), McpAsyncPrompt::withCompletionHandler, promptObjects,
				ModernCompletionJoin.ASYNC_PROMPTS);
	}

	/**
	 * @since 2.1.0
	 */
	public static List<AsyncResourceRegistration> modernResources(List<Object> resourceObjects) {
		return modernResources(resourceObjects, null);
	}

	/**
	 * @param jsonMapper the server's mapper, used for converting MRTR input responses, or
	 * {@code null} for the SDK default
	 * @since 2.1.0
	 */
	public static List<AsyncResourceRegistration> modernResources(List<Object> resourceObjects,
			@Nullable McpJsonMapper jsonMapper) {
		SpringAiAsyncModernResourceProvider provider = new SpringAiAsyncModernResourceProvider(resourceObjects);
		provider.setJsonMapper(jsonMapper);
		return provider.getResources();
	}

	/**
	 * @since 2.1.0
	 */
	public static List<AsyncResourceTemplateRegistration> modernResourceTemplates(List<Object> resourceObjects) {
		return modernResourceTemplates(resourceObjects, null);
	}

	/**
	 * @param jsonMapper the server's mapper, used for converting MRTR input responses, or
	 * {@code null} for the SDK default
	 * @since 2.1.0
	 */
	public static List<AsyncResourceTemplateRegistration> modernResourceTemplates(List<Object> resourceObjects,
			@Nullable McpJsonMapper jsonMapper) {
		SpringAiAsyncModernResourceProvider provider = new SpringAiAsyncModernResourceProvider(resourceObjects);
		provider.setJsonMapper(jsonMapper);
		return provider.getResourceTemplates();
	}

	/**
	 * The resource templates of the {@code @McpResource} methods, each with the
	 * completion handler of the {@code @McpComplete(uri = ...)} method that names its URI
	 * template.
	 * @param completeObjects the beans with {@code @McpComplete} methods
	 * @param jsonMapper the server's mapper, used for converting MRTR input responses, or
	 * {@code null} for the SDK default
	 * @throws IllegalStateException if a {@code @McpComplete} method names a URI that no
	 * {@code @McpResource} template defines, such as the URI of a static resource
	 * @since 2.1.0
	 */
	public static List<AsyncResourceTemplateRegistration> modernResourceTemplates(List<Object> resourceObjects,
			List<Object> completeObjects, @Nullable McpJsonMapper jsonMapper) {
		return ModernCompletionJoin.templates(modernResourceTemplates(resourceObjects, jsonMapper),
				new SpringAiAsyncModernCompleteProvider(completeObjects).getResourceCompletions(),
				template -> template.template().definition().uriTemplate(),
				AsyncResourceTemplateRegistration::withCompletionHandler, resourceObjects,
				ModernCompletionJoin.ASYNC_TEMPLATES);
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

	// RESOURCE TEMPLATE
	public static List<AsyncResourceTemplateSpecification> resourceTemplateSpecifications(
			List<Object> resourceObjects) {
		return new SpringAiAsyncResourceProvider(resourceObjects).getResourceTemplateSpecifications();
	}

	public static List<McpStatelessServerFeatures.AsyncResourceTemplateSpecification> statelessResourceTemplateSpecifications(
			List<Object> resourceObjects) {
		return new SpringAiAsyncStatelessResourceProvider(resourceObjects).getResourceTemplateSpecifications();
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

	}

	private final static class SpringAiAsyncStatelessMcpCompleteProvider extends AsyncStatelessMcpCompleteProvider {

		private SpringAiAsyncStatelessMcpCompleteProvider(List<Object> completeObjects) {
			super(completeObjects);
		}

		@Override
		protected Method[] doGetClassMethods(Object bean) {
			return AnnotationProviderUtil.beanMethods(bean);
		}

	}

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

	// MODERN (2026-07-28) SERVER
	private final static class SpringAiAsyncModernToolProvider extends AsyncModernMcpToolProvider {

		private SpringAiAsyncModernToolProvider(List<Object> toolObjects) {
			super(toolObjects);
		}

		@Override
		protected Method[] doGetClassMethods(Object bean) {
			return AnnotationProviderUtil.beanMethods(bean);
		}

	}

	private final static class SpringAiAsyncModernCompleteProvider extends AsyncModernMcpCompleteProvider {

		private SpringAiAsyncModernCompleteProvider(List<Object> completeObjects) {
			super(completeObjects);
		}

		@Override
		protected Method[] doGetClassMethods(Object bean) {
			return AnnotationProviderUtil.beanMethods(bean);
		}

	}

	private final static class SpringAiAsyncModernPromptProvider extends AsyncModernMcpPromptProvider {

		private SpringAiAsyncModernPromptProvider(List<Object> promptObjects) {
			super(promptObjects);
		}

		@Override
		protected Method[] doGetClassMethods(Object bean) {
			return AnnotationProviderUtil.beanMethods(bean);
		}

	}

	private final static class SpringAiAsyncModernResourceProvider extends AsyncModernMcpResourceProvider {

		private SpringAiAsyncModernResourceProvider(List<Object> resourceObjects) {
			super(resourceObjects);
		}

		@Override
		protected Method[] doGetClassMethods(Object bean) {
			return AnnotationProviderUtil.beanMethods(bean);
		}

	}

}
