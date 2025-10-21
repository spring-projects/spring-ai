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

package org.springframework.ai.model.openaisdk.autoconfigure;

import org.springframework.ai.openaisdk.AbstractOpenAiSdkOptions;
import org.springframework.util.StringUtils;

public final class OpenAiSdkAutoConfigurationUtil {

	private OpenAiSdkAutoConfigurationUtil() {
		// Avoids instantiation
	}

	public static ResolvedConnectionProperties resolveConnectionProperties(AbstractOpenAiSdkOptions commonProperties,
			AbstractOpenAiSdkOptions modelProperties) {

		var resolved = new ResolvedConnectionProperties();

		resolved.setBaseUrl(StringUtils.hasText(modelProperties.getBaseUrl()) ? modelProperties.getBaseUrl()
				: commonProperties.getBaseUrl());

		resolved.setApiKey(StringUtils.hasText(modelProperties.getApiKey()) ? modelProperties.getApiKey()
				: commonProperties.getApiKey());

		String organizationId = StringUtils.hasText(modelProperties.getOrganizationId())
				? modelProperties.getOrganizationId() : commonProperties.getOrganizationId();
		resolved.setOrganizationId(organizationId);

		resolved.setCredential(modelProperties.getCredential() != null ? modelProperties.getCredential()
				: commonProperties.getCredential());

		resolved.setTimeout(
				modelProperties.getTimeout() != null ? modelProperties.getTimeout() : commonProperties.getTimeout());

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

		resolved.setMaxRetries(modelProperties.getMaxRetries() != null ? modelProperties.getMaxRetries()
				: commonProperties.getMaxRetries());

		resolved
			.setProxy(modelProperties.getProxy() != null ? modelProperties.getProxy() : commonProperties.getProxy());

		resolved.setCustomHeaders(modelProperties.getCustomHeaders() != null ? modelProperties.getCustomHeaders()
				: commonProperties.getCustomHeaders());

		return resolved;
	}

	public static class ResolvedConnectionProperties extends AbstractOpenAiSdkOptions {

	}

}
