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

package org.springframework.ai.model.openai.autoconfigure;

import java.lang.reflect.Method;

import org.jspecify.annotations.Nullable;

import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionMessage;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.core.type.MethodMetadata;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * Condition that matches when a usable OpenAI credential source is available for the
 * sub-model bean being defined: a common or sub-prefix api-key property, a programmatic
 * {@code Credential} bean, an {@code OPENAI_API_KEY} or {@code GITHUB_TOKEN} environment
 * variable, or Microsoft Foundry passwordless authentication. The sub-prefix is resolved
 * from the {@code @Bean} method's {@link AbstractOpenAiProperties} parameter type.
 *
 * @author Jewoo Shin
 */
public final class OnAvailableOpenAiConnection extends SpringBootCondition {

	private static final String OPENAI_API_KEY = "OPENAI_API_KEY";

	private static final String OPENAI_BASE_URL = "OPENAI_BASE_URL";

	private static final String AZURE_OPENAI_BASE_URL = "AZURE_OPENAI_BASE_URL";

	private static final String GITHUB_TOKEN = "GITHUB_TOKEN";

	private static final String GITHUB_MODELS_URL = "https://models.github.ai/inference";

	@Override
	public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
		ConditionMessage.Builder message = ConditionMessage.forCondition(getClass().getSimpleName());

		Class<? extends AbstractOpenAiProperties> subPropertiesType = resolveSubPropertiesType(context, metadata);
		if (subPropertiesType == null) {
			return ConditionOutcome
				.noMatch(message.because("@Conditional(OnAvailableOpenAiConnection.class) must be on a @Bean method "
						+ "that declares an AbstractOpenAiProperties sub-type parameter"));
		}

		String subPrefix = resolveSubPrefix(subPropertiesType);
		Environment environment = context.getEnvironment();

		if (hasText(resolve(environment, subPrefix, "api-key"))) {
			return ConditionOutcome.match(message.because("OpenAI api-key is configured"));
		}
		if (hasCredential(context, subPropertiesType)) {
			return ConditionOutcome.match(message.because("OpenAI credential is configured"));
		}

		ModelProvider provider = detectModelProvider(environment, subPrefix);
		if (provider == ModelProvider.MICROSOFT_FOUNDRY) {
			return ConditionOutcome.match(message.because("Microsoft Foundry can use api-key or passwordless auth"));
		}
		// Use System.getenv() to mirror OpenAiSetup.detectApiKey(); the condition
		// and the credential resolver must see the same inputs, otherwise a
		// property-only OPENAI_API_KEY would match here but fail at bean creation
		// (GH-1818).
		if (provider == ModelProvider.GITHUB_MODELS && hasText(System.getenv(GITHUB_TOKEN))) {
			return ConditionOutcome.match(message.because("GITHUB_TOKEN environment variable is configured"));
		}
		if (provider == ModelProvider.OPEN_AI && hasText(System.getenv(OPENAI_API_KEY))) {
			return ConditionOutcome.match(message.because("OPENAI_API_KEY environment variable is configured"));
		}

		return ConditionOutcome.noMatch(message.because("no OpenAI api-key, credential, or supported env fallback"));
	}

	@SuppressWarnings("unchecked")
	private @Nullable Class<? extends AbstractOpenAiProperties> resolveSubPropertiesType(ConditionContext context,
			AnnotatedTypeMetadata metadata) {
		if (!(metadata instanceof MethodMetadata methodMetadata)) {
			return null;
		}
		Class<?> declaringClass = ClassUtils.resolveClassName(methodMetadata.getDeclaringClassName(),
				context.getClassLoader());
		for (Method method : declaringClass.getDeclaredMethods()) {
			if (!method.getName().equals(methodMetadata.getMethodName())) {
				continue;
			}
			for (Class<?> paramType : method.getParameterTypes()) {
				if (AbstractOpenAiProperties.class.isAssignableFrom(paramType)
						&& !OpenAiCommonProperties.class.equals(paramType)) {
					return (Class<? extends AbstractOpenAiProperties>) paramType;
				}
			}
		}
		return null;
	}

	private String resolveSubPrefix(Class<? extends AbstractOpenAiProperties> subPropertiesType) {
		ConfigurationProperties annotation = subPropertiesType.getAnnotation(ConfigurationProperties.class);
		if (annotation == null) {
			throw new IllegalStateException(
					subPropertiesType.getName() + " must be annotated with @ConfigurationProperties");
		}
		String prefix = annotation.prefix();
		return StringUtils.hasText(prefix) ? prefix : annotation.value();
	}

	private ModelProvider detectModelProvider(Environment environment, String subPrefix) {
		if (resolveBoolean(environment, subPrefix, "microsoft-foundry")) {
			return ModelProvider.MICROSOFT_FOUNDRY;
		}
		if (resolveBoolean(environment, subPrefix, "git-hub-models")) {
			return ModelProvider.GITHUB_MODELS;
		}

		String baseUrl = detectBaseUrl(environment, subPrefix);
		if (baseUrl != null && hasText(baseUrl)) {
			if (baseUrl.endsWith("openai.azure.com") || baseUrl.endsWith("openai.azure.com/")
					|| baseUrl.endsWith("cognitiveservices.azure.com")
					|| baseUrl.endsWith("cognitiveservices.azure.com/")) {
				return ModelProvider.MICROSOFT_FOUNDRY;
			}
			if (baseUrl.startsWith(GITHUB_MODELS_URL)) {
				return ModelProvider.GITHUB_MODELS;
			}
		}

		if (hasText(resolve(environment, subPrefix, "microsoft-deployment-name"))
				|| hasText(resolve(environment, subPrefix, "deployment-name"))
				|| hasText(resolve(environment, subPrefix, "microsoft-foundry-service-version"))) {
			return ModelProvider.MICROSOFT_FOUNDRY;
		}

		return ModelProvider.OPEN_AI;
	}

	private @Nullable String detectBaseUrl(Environment environment, String subPrefix) {
		String baseUrl = resolve(environment, subPrefix, "base-url");
		if (!hasText(baseUrl)) {
			baseUrl = System.getenv(OPENAI_BASE_URL);
			String azureBaseUrl = System.getenv(AZURE_OPENAI_BASE_URL);
			if (hasText(azureBaseUrl)) {
				baseUrl = azureBaseUrl;
			}
		}
		return baseUrl;
	}

	private @Nullable String resolve(Environment environment, String subPrefix, String propertyName) {
		String subValue = environment.getProperty(subPrefix + "." + propertyName);
		if (hasText(subValue)) {
			return subValue;
		}
		return environment.getProperty(OpenAiCommonProperties.CONFIG_PREFIX + "." + propertyName);
	}

	private boolean resolveBoolean(Environment environment, String subPrefix, String propertyName) {
		return environment.getProperty(subPrefix + "." + propertyName, Boolean.class, false) || environment
			.getProperty(OpenAiCommonProperties.CONFIG_PREFIX + "." + propertyName, Boolean.class, false);
	}

	private boolean hasCredential(ConditionContext context,
			Class<? extends AbstractOpenAiProperties> subPropertiesType) {
		ListableBeanFactory beanFactory = context.getBeanFactory();
		if (beanFactory == null) {
			return false;
		}
		return hasCredentialIn(beanFactory, OpenAiCommonProperties.class)
				|| hasCredentialIn(beanFactory, subPropertiesType);
	}

	private boolean hasCredentialIn(ListableBeanFactory beanFactory,
			Class<? extends AbstractOpenAiProperties> propertiesType) {
		return beanFactory.getBeansOfType(propertiesType, false, false)
			.values()
			.stream()
			.anyMatch(AbstractOpenAiProperties::hasCredential);
	}

	private boolean hasText(@Nullable String value) {
		return StringUtils.hasText(value);
	}

	private enum ModelProvider {

		OPEN_AI, MICROSOFT_FOUNDRY, GITHUB_MODELS

	}

}
