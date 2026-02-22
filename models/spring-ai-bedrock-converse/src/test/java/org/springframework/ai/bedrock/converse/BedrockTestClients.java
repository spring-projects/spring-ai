/*
 * Copyright 2025-2026 the original author or authors.
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

import java.time.Duration;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

/**
 * Shared test clients for Bedrock integration tests.
 */
@RequiresAwsCredentials
public final class BedrockTestClients {

	private static final Region REGION = Region.US_EAST_1;

	private static final Duration TIMEOUT = Duration.ofSeconds(120);

	private static final DefaultCredentialsProvider CREDENTIALS = DefaultCredentialsProvider.builder().build();

	public static final BedrockRuntimeClient SYNC_CLIENT = BedrockRuntimeClient.builder()
		.credentialsProvider(CREDENTIALS)
		.region(REGION)
		.overrideConfiguration(c -> c.apiCallTimeout(TIMEOUT))
		.build();

	public static final BedrockRuntimeAsyncClient ASYNC_CLIENT = BedrockRuntimeAsyncClient.builder()
		.credentialsProvider(CREDENTIALS)
		.region(REGION)
		.overrideConfiguration(c -> c.apiCallTimeout(TIMEOUT))
		.build();

	private BedrockTestClients() {
	}

}
