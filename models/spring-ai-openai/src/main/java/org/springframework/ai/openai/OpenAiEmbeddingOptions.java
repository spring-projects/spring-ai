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

package org.springframework.ai.openai;

import java.net.Proxy;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import com.openai.azure.AzureOpenAIServiceVersion;
import com.openai.core.JsonValue;
import com.openai.credential.Credential;
import com.openai.models.embeddings.EmbeddingCreateParams;
import com.openai.models.embeddings.EmbeddingModel;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.embedding.EmbeddingOptions;

/**
 * Configuration information for the Embedding Model implementation using the OpenAI Java
 * SDK.
 *
 * @author Julien Dubois
 * @author Christian Tzolov
 * @author Ilayaperumal Gopinathan
 * @author Sebastien Deleuze
 * @author guan xu
 */
public class OpenAiEmbeddingOptions extends AbstractOpenAiOptions implements EmbeddingOptions {

	public static final String DEFAULT_EMBEDDING_MODEL = EmbeddingModel.TEXT_EMBEDDING_ADA_002.asString();

	/**
	 * An identifier for the caller or end user of the operation. This may be used for
	 * tracking or rate-limiting purposes.
	 */
	private final @Nullable String user;

	/**
	 * The format to return the embeddings in. Can be either float or base64.
	 */
	private final @Nullable EncodingFormat encodingFormat;

	/*
	 * The number of dimensions the resulting output embeddings should have. Only
	 * supported in `text-embedding-3` and later models.
	 */
	private final @Nullable Integer dimensions;

	/**
	 * Extra parameters that are not part of the standard OpenAI API. These parameters are
	 * passed as additional body properties to support OpenAI-compatible providers like
	 * vLLM, Ollama, Groq, etc. that support custom parameters such as top_k,
	 * repetition_penalty, etc.
	 */
	private final @Nullable Map<String, Object> extraBody;

	protected OpenAiEmbeddingOptions(@Nullable String baseUrl, @Nullable String apiKey, @Nullable Credential credential,
			@Nullable String model, @Nullable String microsoftDeploymentName,
			@Nullable AzureOpenAIServiceVersion microsoftFoundryServiceVersion, @Nullable String organizationId,
			@Nullable Boolean isMicrosoftFoundry, @Nullable Boolean isGitHubModels, @Nullable Duration timeout,
			@Nullable Integer maxRetries, @Nullable Proxy proxy, @Nullable Map<String, String> customHeaders,
			@Nullable String user, @Nullable EncodingFormat encodingFormat, @Nullable Integer dimensions,
			@Nullable Map<String, Object> extraBody) {
		super(baseUrl, apiKey, credential, model != null ? model : DEFAULT_EMBEDDING_MODEL, microsoftDeploymentName,
				microsoftFoundryServiceVersion, organizationId, isMicrosoftFoundry, isGitHubModels, timeout, maxRetries,
				proxy, customHeaders);
		this.user = user;
		this.encodingFormat = encodingFormat;
		this.dimensions = dimensions;
		this.extraBody = (extraBody != null ? Map.copyOf(extraBody) : null);
	}

	public static Builder builder() {
		return new Builder();
	}

	public @Nullable String getUser() {
		return this.user;
	}

	public @Nullable EncodingFormat getEncodingFormat() {
		return this.encodingFormat;
	}

	@Override
	public @Nullable Integer getDimensions() {
		return this.dimensions;
	}

	public @Nullable Map<String, Object> getExtraBody() {
		return this.extraBody;
	}

	public EmbeddingCreateParams toOpenAiCreateParams(List<String> instructions) {

		EmbeddingCreateParams.Builder builder = EmbeddingCreateParams.builder();

		// Use deployment name if available (for Microsoft Foundry), otherwise use model
		// name
		if (this.getDeploymentName() != null) {
			builder.model(this.getDeploymentName());
		}
		else if (this.getModel() != null) {
			builder.model(this.getModel());
		}

		if (!instructions.isEmpty()) {
			builder.input(EmbeddingCreateParams.Input.ofArrayOfStrings(instructions));
		}
		if (this.getUser() != null) {
			builder.user(this.getUser());
		}
		if (this.getEncodingFormat() != null) {
			builder.encodingFormat(EmbeddingCreateParams.EncodingFormat.of(this.getEncodingFormat().getValue()));
		}
		if (this.getDimensions() != null) {
			builder.dimensions(this.getDimensions());
		}

		// Add extraBody parameters as additional body properties for OpenAI-compatible
		// providers
		if (this.getExtraBody() != null && !this.getExtraBody().isEmpty()) {
			Map<String, JsonValue> extraParams = this.getExtraBody()
				.entrySet()
				.stream()
				.collect(Collectors.toMap(Map.Entry::getKey, entry -> JsonValue.from(entry.getValue())));
			builder.additionalBodyProperties(extraParams);
		}

		return builder.build();
	}

	/**
	 * The format to return the embeddings in. Can be either float or base64.
	 */
	public enum EncodingFormat {

		FLOAT("float"), BASE64("base64");

		private final String value;

		EncodingFormat(String value) {
			this.value = value;
		}

		public String getValue() {
			return this.value;
		}

	}

	public static final class Builder extends AbstractBuilder<OpenAiEmbeddingOptions, Builder> {

		private @Nullable String user;

		private @Nullable EncodingFormat encodingFormat;

		private @Nullable Integer dimensions;

		private @Nullable Map<String, Object> extraBody;

		public Builder from(OpenAiEmbeddingOptions fromOptions) {
			// Parent class fields
			this.baseUrl = fromOptions.getBaseUrl();
			this.apiKey = fromOptions.getApiKey();
			this.credential = fromOptions.getCredential();
			this.model = fromOptions.getModel();
			this.microsoftDeploymentName = fromOptions.getDeploymentName();
			this.microsoftFoundryServiceVersion = fromOptions.getMicrosoftFoundryServiceVersion();
			this.organizationId = fromOptions.getOrganizationId();
			this.isMicrosoftFoundry = fromOptions.isMicrosoftFoundry();
			this.isGitHubModels = fromOptions.isGitHubModels();
			this.timeout = fromOptions.getTimeout();
			this.maxRetries = fromOptions.getMaxRetries();
			this.proxy = fromOptions.getProxy();
			this.customHeaders = fromOptions.getCustomHeaders();
			// Child class fields
			this.user = fromOptions.getUser();
			this.encodingFormat = fromOptions.getEncodingFormat();
			this.dimensions = fromOptions.getDimensions();
			this.extraBody = fromOptions.getExtraBody();
			return this;
		}

		public Builder merge(@Nullable EmbeddingOptions from) {
			if (from == null) {
				return this;
			}
			if (from.getModel() != null) {
				this.model = from.getModel();
			}
			if (from.getDimensions() != null) {
				this.dimensions = from.getDimensions();
			}
			if (from instanceof AbstractOpenAiOptions castFrom) {
				if (castFrom.getBaseUrl() != null) {
					this.baseUrl = castFrom.getBaseUrl();
				}
				if (castFrom.getApiKey() != null) {
					this.apiKey = castFrom.getApiKey();
				}
				if (castFrom.getCredential() != null) {
					this.credential = castFrom.getCredential();
				}
				if (castFrom.getDeploymentName() != null) {
					this.microsoftDeploymentName = castFrom.getDeploymentName();
				}
				if (castFrom.getMicrosoftFoundryServiceVersion() != null) {
					this.microsoftFoundryServiceVersion = castFrom.getMicrosoftFoundryServiceVersion();
				}
				if (castFrom.getOrganizationId() != null) {
					this.organizationId = castFrom.getOrganizationId();
				}
				this.isMicrosoftFoundry = castFrom.isMicrosoftFoundry();
				this.isGitHubModels = castFrom.isGitHubModels();
				this.timeout = castFrom.getTimeout();
				this.maxRetries = castFrom.getMaxRetries();
				if (castFrom.getProxy() != null) {
					this.proxy = castFrom.getProxy();
				}
				if (castFrom.getCustomHeaders() != null) {
					if (this.customHeaders == null) {
						this.customHeaders = new HashMap<>(castFrom.getCustomHeaders());
					}
					else {
						Map<String, String> merged = new HashMap<>(this.customHeaders);
						merged.putAll(castFrom.getCustomHeaders());
						this.customHeaders = merged;
					}
				}
			}
			if (from instanceof OpenAiEmbeddingOptions castFrom) {
				if (castFrom.getUser() != null) {
					this.user = castFrom.getUser();
				}
				if (castFrom.getEncodingFormat() != null) {
					this.encodingFormat = castFrom.getEncodingFormat();
				}
				if (castFrom.getExtraBody() != null) {
					if (this.extraBody == null) {
						this.extraBody = new HashMap<>(castFrom.getExtraBody());
					}
					else {
						Map<String, Object> merged = new HashMap<>(this.extraBody);
						merged.putAll(castFrom.getExtraBody());
						this.extraBody = merged;
					}
				}
			}
			return this;
		}

		public Builder from(EmbeddingCreateParams openAiCreateParams) {

			if (openAiCreateParams.user().isPresent()) {
				this.user = openAiCreateParams.user().get();
			}
			if (openAiCreateParams.encodingFormat().isPresent()) {
				this.encodingFormat = EncodingFormat
					.valueOf(openAiCreateParams.encodingFormat().get().asString().toUpperCase(Locale.ROOT));
			}
			if (openAiCreateParams.dimensions().isPresent()) {
				this.dimensions = Math.toIntExact(openAiCreateParams.dimensions().get());
			}
			return this;
		}

		public Builder user(@Nullable String user) {
			this.user = user;
			return this;
		}

		public Builder encodingFormat(@Nullable EncodingFormat encodingFormat) {
			this.encodingFormat = encodingFormat;
			return this;
		}

		public Builder dimensions(@Nullable Integer dimensions) {
			this.dimensions = dimensions;
			return this;
		}

		public Builder extraBody(@Nullable Map<String, Object> extraBody) {
			this.extraBody = extraBody;
			return this;
		}

		@Override
		public OpenAiEmbeddingOptions build() {
			return new OpenAiEmbeddingOptions(this.baseUrl, this.apiKey, this.credential, this.model,
					this.microsoftDeploymentName, this.microsoftFoundryServiceVersion, this.organizationId,
					this.isMicrosoftFoundry, this.isGitHubModels, this.timeout, this.maxRetries, this.proxy,
					this.customHeaders, this.user, this.encodingFormat, this.dimensions, this.extraBody);
		}

	}

}
