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

import java.time.Duration;

import org.springframework.util.StringUtils;

public final class OpenAiAutoConfigurationUtil {

	private OpenAiAutoConfigurationUtil() {
		// Avoids instantiation
	}

	public static ResolvedConnectionProperties resolveCommonProperties(AbstractOpenAiProperties commonProperties,
			AbstractOpenAiProperties modelProperties) {

		var resolved = new ResolvedConnectionProperties();

		resolved.setBaseUrl(StringUtils.hasText(modelProperties.getBaseUrl()) ? modelProperties.getBaseUrl()
				: commonProperties.getBaseUrl());

		// An explicit empty string ("") is a deliberate no-auth signal (NoopApiKey
		// behaviour) and must be preserved. Only fall back to commonProperties when the
		// model-level key is null (i.e. not configured at all).
		resolved.setApiKey(
				modelProperties.getApiKey() != null ? modelProperties.getApiKey() : commonProperties.getApiKey());

		String organizationId = StringUtils.hasText(modelProperties.getOrganizationId())
				? modelProperties.getOrganizationId() : commonProperties.getOrganizationId();
		resolved.setOrganizationId(organizationId);

		resolved.setCredential(modelProperties.getCredential() != null ? modelProperties.getCredential()
				: commonProperties.getCredential());

		// A null timeout means "not configured", so the model-level value only wins when
		// it was actually set. The resolved value is always non-null: it is what the
		// OpenAI client is built with.
		Duration timeout = modelProperties.getTimeout() != null ? modelProperties.getTimeout()
				: commonProperties.getTimeout();
		resolved.setTimeout(timeout != null ? timeout : OpenAiCommonProperties.DEFAULT_TIMEOUT);

		resolved.setModel(StringUtils.hasText(modelProperties.getModel()) ? modelProperties.getModel()
				: commonProperties.getModel());

		resolved.setMicrosoftDeploymentName(StringUtils.hasText(modelProperties.getMicrosoftDeploymentName())
				? modelProperties.getMicrosoftDeploymentName() : commonProperties.getMicrosoftDeploymentName());

		resolved.setMicrosoftFoundryServiceVersion(modelProperties.getMicrosoftFoundryServiceVersion() != null
				? modelProperties.getMicrosoftFoundryServiceVersion()
				: commonProperties.getMicrosoftFoundryServiceVersion());

		// For boolean properties, use modelProperties value, defaulting to
		// commonProperties if needed
		resolved.setMicrosoftFoundry(modelProperties.isMicrosoftFoundry() || commonProperties.isMicrosoftFoundry());

		resolved.setGitHubModels(modelProperties.isGitHubModels() || commonProperties.isGitHubModels());

		resolved.setMaxRetries(modelProperties.getMaxRetries() != OpenAiCommonProperties.DEFAULT_MAX_RETRIES
				? modelProperties.getMaxRetries() : commonProperties.getMaxRetries());

		resolved
			.setProxy(modelProperties.getProxy() != null ? modelProperties.getProxy() : commonProperties.getProxy());

		resolved.setCustomHeaders(!modelProperties.getCustomHeaders().isEmpty() ? modelProperties.getCustomHeaders()
				: commonProperties.getCustomHeaders());

		return resolved;
	}

	public static class ResolvedConnectionProperties extends OpenAiCommonProperties {

		/**
		 * Unlike the properties it is resolved from, a resolved timeout is always
		 * present: it is what the OpenAI client is built with.
		 */
		@Override
		public Duration getTimeout() {
			Duration timeout = super.getTimeout();
			return timeout != null ? timeout : DEFAULT_TIMEOUT;
		}

	}

}
