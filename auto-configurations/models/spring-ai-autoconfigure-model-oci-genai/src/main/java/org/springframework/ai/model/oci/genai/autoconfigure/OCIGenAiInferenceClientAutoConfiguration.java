/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.model.oci.genai.autoconfigure;

import java.io.IOException;

import com.oracle.bmc.ClientConfiguration;
import com.oracle.bmc.Region;
import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider;
import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider;
import com.oracle.bmc.auth.SimplePrivateKeySupplier;
import com.oracle.bmc.auth.okeworkloadidentity.OkeWorkloadIdentityAuthenticationDetailsProvider;
import com.oracle.bmc.generativeaiinference.GenerativeAiInferenceClient;
import com.oracle.bmc.retrier.RetryConfiguration;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

/**
 * {@link AutoConfiguration Auto-configuration} for Oracle Cloud Infrastructure Generative
 * AI Inference Client.
 *
 * @author Anders Swanson
 * @author Ilayaperumal Gopinathan
 */
@AutoConfiguration
@ConditionalOnClass(GenerativeAiInferenceClient.class)
@EnableConfigurationProperties(OCIConnectionProperties.class)
public class OCIGenAiInferenceClientAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public GenerativeAiInferenceClient generativeAiInferenceClient(OCIConnectionProperties properties)
			throws IOException {
		ClientConfiguration.ClientConfigurationBuilder clientConfigurationBuilder = ClientConfiguration.builder()
			.retryConfiguration(RetryConfiguration.SDK_DEFAULT_RETRY_CONFIGURATION);

		if (properties.getConnectTimeout() != null) {
			clientConfigurationBuilder.connectionTimeoutMillis(properties.getConnectTimeout().toMillisPart());
		}
		if (properties.getReadTimeout() != null) {
			clientConfigurationBuilder.readTimeoutMillis(properties.getReadTimeout().toMillisPart());
		}
		if (properties.getMaxAsyncThreads() != null) {
			clientConfigurationBuilder.maxAsyncThreads(properties.getMaxAsyncThreads());
		}
		ClientConfiguration clientConfiguration = clientConfigurationBuilder.build();

		GenerativeAiInferenceClient.Builder builder = GenerativeAiInferenceClient.builder()
			.configuration(clientConfiguration);
		if (StringUtils.hasText(properties.getRegion())) {
			builder.region(Region.valueOf(properties.getRegion()));
		}
		if (StringUtils.hasText(properties.getEndpoint())) {
			builder.endpoint(properties.getEndpoint());
		}
		return builder.build(authenticationProvider(properties));
	}

	private static BasicAuthenticationDetailsProvider authenticationProvider(OCIConnectionProperties properties)
			throws IOException {
		return switch (properties.getAuthenticationType()) {
			case FILE -> new ConfigFileAuthenticationDetailsProvider(properties.getFile(), properties.getProfile());
			case INSTANCE_PRINCIPAL -> InstancePrincipalsAuthenticationDetailsProvider.builder().build();
			case WORKLOAD_IDENTITY -> OkeWorkloadIdentityAuthenticationDetailsProvider.builder().build();
			case SIMPLE -> SimpleAuthenticationDetailsProvider.builder()
				.userId(properties.getUserId())
				.tenantId(properties.getTenantId())
				.fingerprint(properties.getFingerprint())
				.privateKeySupplier(new SimplePrivateKeySupplier(properties.getPrivateKey()))
				.passPhrase(properties.getPassPhrase())
				.region(Region.valueOf(properties.getRegion()))
				.build();
		};
	}

}
