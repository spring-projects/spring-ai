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

package org.springframework.ai.model.tool.autoconfigure;

import java.util.ArrayList;
import java.util.List;

import io.micrometer.observation.ObservationRegistry;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.execution.DefaultToolExecutionExceptionProcessor;
import org.springframework.ai.tool.execution.ToolExecutionExceptionProcessor;
import org.springframework.ai.tool.observation.ToolCallingContentObservationFilter;
import org.springframework.ai.tool.observation.ToolCallingObservationConvention;
import org.springframework.ai.tool.resolution.DelegatingToolCallbackResolver;
import org.springframework.ai.tool.resolution.SpringBeanToolCallbackResolver;
import org.springframework.ai.tool.resolution.StaticToolCallbackResolver;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import org.springframework.ai.util.JacksonUtils;
import org.springframework.ai.util.json.JsonParser;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.ResolvableType;
import org.springframework.util.ClassUtils;

/**
 * Auto-configuration for common tool calling features of {@link ChatModel}.
 *
 * @author Thomas Vitale
 * @author Christian Tzolov
 * @author Daniel Garnier-Moiroux
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnClass(ChatModel.class)
@EnableConfigurationProperties(ToolCallingProperties.class)
public class ToolCallingAutoConfiguration {

	private static final Logger logger = LoggerFactory.getLogger(ToolCallingAutoConfiguration.class);

	/**
	 * The default {@link ToolCallbackResolver} resolves tools by name for methods,
	 * functions, and {@link ToolCallbackProvider} beans.
	 * <p>
	 * MCP providers are excluded, to avoid initializing them early with #listTools().
	 */
	@Bean
	@ConditionalOnMissingBean
	ToolCallbackResolver toolCallbackResolver(
			GenericApplicationContext applicationContext, // @formatter:off
			List<ToolCallback> toolCallbacks,
			// Deprecated in favor of the tcbProviders. Kept for backward compatibility.
			ObjectProvider<List<ToolCallbackProvider>> tcbProviderList,
			ObjectProvider<ToolCallbackProvider> tcbProviders) { // @formatter:on

		List<ToolCallback> allFunctionAndToolCallbacks = new ArrayList<>(toolCallbacks);

		// Merge ToolCallbackProviders from both ObjectProviders.
		List<ToolCallbackProvider> totalToolCallbackProviders = new ArrayList<>(
				tcbProviderList.stream().flatMap(List::stream).toList());
		totalToolCallbackProviders.addAll(tcbProviders.stream().toList());

		// De-duplicate ToolCallbackProviders
		totalToolCallbackProviders = totalToolCallbackProviders.stream().distinct().toList();

		totalToolCallbackProviders.stream()
			.filter(pr -> !isMcpToolCallbackProvider(ResolvableType.forInstance(pr)))
			.map(pr -> List.of(pr.getToolCallbacks()))
			.forEach(allFunctionAndToolCallbacks::addAll);

		var staticToolCallbackResolver = new StaticToolCallbackResolver(allFunctionAndToolCallbacks);

		var springBeanToolCallbackResolver = SpringBeanToolCallbackResolver.builder()
			.applicationContext(applicationContext)
			.build();

		return new DelegatingToolCallbackResolver(List.of(staticToolCallbackResolver, springBeanToolCallbackResolver));
	}

	private static boolean isMcpToolCallbackProvider(ResolvableType type) {
		if (type.getType().getTypeName().equals("org.springframework.ai.mcp.SyncMcpToolCallbackProvider")
				|| type.getType().getTypeName().equals("org.springframework.ai.mcp.AsyncMcpToolCallbackProvider")) {
			return true;
		}
		var superType = type.getSuperType();
		return superType != ResolvableType.NONE && isMcpToolCallbackProvider(superType);
	}

	@Bean
	@ConditionalOnMissingBean
	ToolExecutionExceptionProcessor toolExecutionExceptionProcessor(ToolCallingProperties properties) {
		ArrayList<Class<? extends RuntimeException>> rethrownExceptions = new ArrayList<>();

		// ClientAuthorizationException is used by Spring Security in oauth2 flows,
		// for example with ServletOAuth2AuthorizedClientExchangeFilterFunction and
		// OAuth2ClientHttpRequestInterceptor.
		Class<? extends RuntimeException> oauth2Exception = getClassOrNull(
				"org.springframework.security.oauth2.client.ClientAuthorizationException");
		if (oauth2Exception != null) {
			rethrownExceptions.add(oauth2Exception);
		}

		return DefaultToolExecutionExceptionProcessor.builder()
			.alwaysThrow(properties.isThrowExceptionOnError())
			.rethrowExceptions(rethrownExceptions)
			.build();
	}

	@Bean
	@ConditionalOnMissingBean
	ToolCallingManager toolCallingManager(ToolCallbackResolver toolCallbackResolver,
			ToolExecutionExceptionProcessor toolExecutionExceptionProcessor,
			ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<ToolCallingObservationConvention> observationConvention) {
		var toolCallingManager = ToolCallingManager.builder()
			.observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
			.toolCallbackResolver(toolCallbackResolver)
			.toolExecutionExceptionProcessor(toolExecutionExceptionProcessor)
			.build();

		observationConvention.ifAvailable(toolCallingManager::setObservationConvention);

		return toolCallingManager;
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = ToolCallingProperties.CONFIG_PREFIX + ".observations", name = "include-content",
			havingValue = "true")
	ToolCallingContentObservationFilter toolCallingContentObservationFilter() {
		logger.warn(
				"You have enabled the inclusion of the tool call arguments and result in the observations, with the risk of exposing sensitive or private information. Please, be careful!");
		return new ToolCallingContentObservationFilter();
	}

	/**
	 * {@link JsonMapper} used by {@link JsonParser} for tool-call arguments and
	 * structured output parsing. Declare a bean with the same name to replace the default
	 * (e.g. to enable {@code JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS} for models
	 * that emit raw newlines in tool-call arguments).
	 */
	@Bean(name = "jsonParserJsonMapper", defaultCandidate = false)
	@ConditionalOnMissingBean(name = "jsonParserJsonMapper")
	JsonMapper jsonParserJsonMapper() {
		return JsonMapper.builder()
			.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
			.disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
			.addModules(JacksonUtils.instantiateAvailableModules())
			.build();
	}

	/**
	 * {@link JsonMapper} used by {@link ModelOptionsUtils} for model option and response
	 * JSON conversions. Declare a bean with the same name to replace the default.
	 */
	@Bean(name = "modelOptionsJsonMapper", defaultCandidate = false)
	@ConditionalOnMissingBean(name = "modelOptionsJsonMapper")
	JsonMapper modelOptionsJsonMapper() {
		return JsonMapper.builder()
			.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
			.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
			.addModules(JacksonUtils.instantiateAvailableModules())
			.build();
	}

	/**
	 * Wires the named {@code jsonParserJsonMapper} and {@code modelOptionsJsonMapper}
	 * beans (either the defaults above or user-supplied overrides) into the static
	 * {@link JsonParser} and {@link ModelOptionsUtils} utilities, so the lookups done by
	 * {@link org.springframework.ai.tool.method.MethodToolCallback MethodToolCallback}
	 * and model-options code respect application-level Jackson configuration.
	 */
	@Bean
	InitializingBean springAiJsonMapperInitializer(@Qualifier("jsonParserJsonMapper") JsonMapper jsonParserJsonMapper,
			@Qualifier("modelOptionsJsonMapper") JsonMapper modelOptionsJsonMapper) {
		return () -> {
			JsonParser.setJsonMapper(jsonParserJsonMapper);
			ModelOptionsUtils.setJsonMapper(modelOptionsJsonMapper);
		};
	}

	private static @Nullable Class<? extends RuntimeException> getClassOrNull(String className) {
		try {
			Class<?> clazz = ClassUtils.forName(className, null);
			if (RuntimeException.class.isAssignableFrom(clazz)) {
				return (Class<? extends RuntimeException>) clazz;
			}
			else {
				logger.debug("Class {} is not a subclass of RuntimeException", className);
			}
		}
		catch (ClassNotFoundException e) {
			logger.debug("Cannot load class: {}", className);
		}
		catch (Exception e) {
			logger.debug("Error loading class: {}", className, e);
		}
		return null;
	}

}
