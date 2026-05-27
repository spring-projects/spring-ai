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

package org.springframework.ai.bedrock.converse;

import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;

/**
 * Global interceptor auto-loaded by the AWS SDK via service loader. Configures the
 * User-Agent app ID in its static initializer so every SDK client picks it up.
 *
 * @author Matt Meckes
 */
public final class BedrockUserAgentInterceptor implements ExecutionInterceptor {

	static {
		UserAgentProvider.configure();
	}

	@Override
	public SdkRequest modifyRequest(Context.ModifyRequest context, ExecutionAttributes executionAttributes) {
		return context.request();
	}

}
