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

package org.springframework.ai.vectorstore.opensearch.autoconfigure;

import org.springframework.boot.autoconfigure.condition.ConditionMessage;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Condition that matches if either:
 * <ul>
 * <li>The property <code>spring.ai.vectorstore.opensearch.aws.enabled</code> is
 * explicitly set to <code>false</code>.</li>
 * <li>Required AWS SDK classes are missing from the classpath.</li>
 * </ul>
 * <p>
 * This enables the non-AWS OpenSearch auto-configuration to be activated when the user
 * disables AWS support via property or when AWS SDKs are not present, ensuring correct
 * fallback behavior for non-AWS OpenSearch usage.
 */
public class OpenSearchNonAwsCondition extends SpringBootCondition {

	private static final String AWS_ENABLED_PROPERTY = "spring.ai.vectorstore.opensearch.aws.enabled";

	@Override
	public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
		// 1. If AWS property is set to false, match
		String awsEnabled = context.getEnvironment().getProperty(AWS_ENABLED_PROPERTY);
		if ("false".equalsIgnoreCase(awsEnabled)) {
			return ConditionOutcome.match(ConditionMessage.forCondition("OpenSearchNonAwsCondition")
				.because("Property 'spring.ai.vectorstore.opensearch.aws.enabled' is false"));
		}
		// 2. If AWS SDK classes are missing, match
		boolean awsClassesPresent = isPresent("software.amazon.awssdk.auth.credentials.AwsCredentialsProvider")
				&& isPresent("software.amazon.awssdk.regions.Region")
				&& isPresent("software.amazon.awssdk.http.apache.ApacheHttpClient");
		if (!awsClassesPresent) {
			return ConditionOutcome.match(
					ConditionMessage.forCondition("OpenSearchNonAwsCondition").because("AWS SDK classes are missing"));
		}
		// 3. Otherwise, do not match
		return ConditionOutcome.noMatch(ConditionMessage.forCondition("OpenSearchNonAwsCondition")
			.because("AWS SDK classes are present and property is not false"));
	}

	private boolean isPresent(String className) {
		try {
			Class.forName(className, false, getClass().getClassLoader());
			return true;
		}
		catch (ClassNotFoundException ex) {
			return false;
		}
	}

}
