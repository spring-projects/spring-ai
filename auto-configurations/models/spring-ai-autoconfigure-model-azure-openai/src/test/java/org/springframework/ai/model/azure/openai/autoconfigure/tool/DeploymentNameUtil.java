/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.model.azure.openai.autoconfigure.tool;

import org.springframework.util.StringUtils;

public final class DeploymentNameUtil {

	private DeploymentNameUtil() {

	}

	public static String getDeploymentName() {
		String deploymentName = System.getenv("AZURE_OPENAI_DEPLOYMENT_NAME");
		if (StringUtils.hasText(deploymentName)) {
			return deploymentName;
		}
		else {
			return "gpt-4o";
		}
	}

}
