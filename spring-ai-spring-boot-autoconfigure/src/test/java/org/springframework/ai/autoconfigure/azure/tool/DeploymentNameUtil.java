package org.springframework.ai.autoconfigure.azure.tool;

import org.springframework.util.StringUtils;

public class DeploymentNameUtil {

	public static String getDeploymentName() {
		String deploymentName = System.getenv("AZURE_OPENAI_DEPLOYMENT_NAME");
		if (StringUtils.hasText(deploymentName)) {
			return deploymentName;
		}
		else {
			return "gpt-4-0125-preview";
		}
	}

}
