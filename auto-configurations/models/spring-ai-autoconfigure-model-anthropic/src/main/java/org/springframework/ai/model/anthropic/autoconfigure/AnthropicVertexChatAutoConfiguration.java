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

package org.springframework.ai.model.anthropic.autoconfigure;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.anthropic.backends.Backend;
import com.anthropic.vertex.backends.VertexBackend;
import com.google.auth.oauth2.GoogleCredentials;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.http.okhttp.AnthropicHttpClientBuilderCustomizer;
import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.model.SpringAIModels;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;

/**
 * Auto-configuration for {@link AnthropicChatModel} when using Anthropic Claude models
 * through Google Cloud Vertex AI.
 *
 * <p>
 * Activates only when:
 * <ul>
 * <li>{@link VertexBackend} is on the classpath (requires
 * {@code com.anthropic:anthropic-java-vertex})</li>
 * <li>{@code spring.ai.anthropic.backend=vertex-ai} is set</li>
 * </ul>
 *
 * <p>
 * This configuration builds a {@link VertexBackend} from the
 * {@link AnthropicConnectionProperties.Vertex} nested properties, then injects it into
 * {@link AnthropicChatModel} via {@link AnthropicChatModel.Builder#backend(Backend)}. The
 * existing {@link AnthropicChatAutoConfiguration} handles the default
 * {@code spring.ai.anthropic.backend=anthropic} path.
 *
 * @author dragonfsky
 * @since 2.0.0
 */
@AutoConfiguration
@ConditionalOnClass(name = { "com.anthropic.client.AnthropicClient", "com.anthropic.vertex.backends.VertexBackend" })
@ConditionalOnProperty(name = SpringAIModelProperties.CHAT_MODEL, havingValue = SpringAIModels.ANTHROPIC,
		matchIfMissing = true)
@ConditionalOnProperty(prefix = AnthropicConnectionProperties.CONFIG_PREFIX, name = "backend",
		havingValue = "vertex-ai")
@EnableConfigurationProperties({ AnthropicConnectionProperties.class, AnthropicChatProperties.class })
public class AnthropicVertexChatAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	AnthropicChatModel anthropicVertexChatModel(AnthropicConnectionProperties connectionProperties,
			AnthropicChatProperties chatProperties, ToolCallingManager toolCallingManager,
			ObjectProvider<ObservationRegistry> observationRegistry, ObjectProvider<MeterRegistry> meterRegistry,
			ObjectProvider<AnthropicHttpClientBuilderCustomizer> httpClientBuilderCustomizers) {

		AnthropicChatOptions.Builder optionsBuilder = chatProperties.toOptions().mutate();

		// Apply connection-level settings (timeout, retries, proxy, custom headers)
		// but NOT api-key or base-url — those conflict with Vertex.
		if (connectionProperties.getTimeout() != null) {
			optionsBuilder.timeout(connectionProperties.getTimeout());
		}
		if (connectionProperties.getMaxRetries() != null) {
			optionsBuilder.maxRetries(connectionProperties.getMaxRetries());
		}
		if (connectionProperties.getProxy() != null) {
			optionsBuilder.proxy(connectionProperties.getProxy());
		}
		if (!connectionProperties.getCustomHeaders().isEmpty()) {
			optionsBuilder.customHeaders(connectionProperties.getCustomHeaders());
		}

		AnthropicChatOptions options = optionsBuilder.build();

		// Fail-fast: api-key and base-url make no sense with Vertex AI.
		if (connectionProperties.getApiKey() != null) {
			throw new IllegalStateException(
					"spring.ai.anthropic.api-key must not be configured when spring.ai.anthropic.backend=vertex-ai. "
							+ "Vertex AI uses Google Cloud credentials, not an Anthropic API key.");
		}
		if (connectionProperties.getBaseUrl() != null) {
			throw new IllegalStateException(
					"spring.ai.anthropic.base-url must not be configured when spring.ai.anthropic.backend=vertex-ai. "
							+ "The endpoint is derived from the Vertex project and region.");
		}

		// Reject headers that would interfere with Vertex auth in both
		// connection-level customHeaders and per-request chat.httpHeaders.
		assertNoVertexForbiddenHeaders(connectionProperties.getCustomHeaders(), "spring.ai.anthropic.custom-headers");
		assertNoVertexForbiddenHeaders(chatProperties.getHttpHeaders(), "spring.ai.anthropic.chat.http-headers");

		Backend backend = createVertexBackend(connectionProperties);

		ObservationRegistry obsReg = observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP);
		MeterRegistry metReg = chatProperties.isConnectionPoolMetricsEnabled() ? meterRegistry.getIfAvailable() : null;

		List<AnthropicHttpClientBuilderCustomizer> customizers = httpClientBuilderCustomizers.orderedStream()
			.collect(Collectors.toList());

		AnthropicChatModel chatModel = AnthropicChatModel.builder()
			.options(options)
			.backend(backend)
			.toolCallingManager(toolCallingManager)
			.observationRegistry(obsReg)
			.meterRegistry(metReg)
			.httpClientBuilderCustomizers(customizers)
			.build();

		return chatModel;
	}

	private static final Log logger = LogFactory.getLog(AnthropicVertexChatAutoConfiguration.class);

	private static void assertNoVertexForbiddenHeaders(Map<String, String> headers, String propertyName) {
		if (headers == null || headers.isEmpty()) {
			return;
		}
		for (String header : headers.keySet()) {
			String normalized = header.toLowerCase(java.util.Locale.ROOT);
			if (normalized.equals("authorization") || normalized.equals("x-api-key")
					|| normalized.equals("anthropic-version")) {
				throw new IllegalStateException("'" + propertyName + "." + header + "' must not be configured when "
						+ "spring.ai.anthropic.backend=vertex-ai. "
						+ "Vertex AI manages Authorization, x-api-key, and anthropic-version automatically.");
			}
		}
	}

	/**
	 * Creates a {@link VertexBackend} from the configured properties. Falls back to
	 * {@link VertexBackend#fromEnv()} when no explicit configuration is provided.
	 */
	@SuppressWarnings("NullAway")
	private static Backend createVertexBackend(AnthropicConnectionProperties properties) {
		AnthropicConnectionProperties.Vertex vertex = properties.getVertex();

		boolean hasProjectId = hasText(vertex.getProjectId());
		boolean hasLocation = hasText(vertex.getLocation());
		boolean hasCredentials = vertex.getCredentialsUri() != null;

		if (!hasProjectId && !hasLocation && !hasCredentials) {
			logger.debug("No explicit Vertex AI configuration; using VertexBackend.fromEnv()");
			return VertexBackend.fromEnv();
		}

		if (!hasProjectId || !hasLocation) {
			throw new IllegalStateException(
					"spring.ai.anthropic.vertex.project-id and spring.ai.anthropic.vertex.location "
							+ "must be set together when any spring.ai.anthropic.vertex.* property is configured. "
							+ "Alternatively, omit all vertex.* properties to use VertexBackend.fromEnv().");
		}

		GoogleCredentials credentials = resolveGoogleCredentials(vertex);

		String projectId = vertex.getProjectId();
		String location = vertex.getLocation();
		return VertexBackend.builder().project(projectId).region(location).googleCredentials(credentials).build();
	}

	private static boolean hasText(@Nullable String value) {
		return value != null && !value.isBlank();
	}

	private static GoogleCredentials resolveGoogleCredentials(AnthropicConnectionProperties.Vertex vertex) {
		Resource credentialsResource = vertex.getCredentialsUri();
		if (credentialsResource != null) {
			try (InputStream credentialsStream = credentialsResource.getInputStream()) {
				return GoogleCredentials.fromStream(credentialsStream);
			}
			catch (IOException ex) {
				throw new IllegalStateException(
						"Failed to read Google credentials from spring.ai.anthropic.vertex.credentials-uri: "
								+ credentialsResource,
						ex);
			}
		}

		try {
			return GoogleCredentials.getApplicationDefault();
		}
		catch (IOException ex) {
			throw new IllegalStateException(
					"Application Default Credentials are required when " + "spring.ai.anthropic.backend=vertex-ai. "
							+ "Set spring.ai.anthropic.vertex.credentials-uri or configure "
							+ "GOOGLE_APPLICATION_CREDENTIALS.",
					ex);
		}
	}

}
