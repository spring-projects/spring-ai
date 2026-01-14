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

package org.springframework.ai.azure.openai;

import java.util.List;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.models.ImageGenerationOptions;
import com.azure.ai.openai.models.ImageGenerationQuality;
import com.azure.ai.openai.models.ImageGenerationResponseFormat;
import com.azure.ai.openai.models.ImageGenerationStyle;
import com.azure.ai.openai.models.ImageSize;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.azure.openai.metadata.AzureOpenAiImageGenerationMetadata;
import org.springframework.ai.azure.openai.metadata.AzureOpenAiImageResponseMetadata;
import org.springframework.ai.image.Image;
import org.springframework.ai.image.ImageGeneration;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.image.ImageResponseMetadata;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.util.JacksonUtils;
import org.springframework.util.Assert;

/**
 * {@link ImageModel} implementation for {@literal Microsoft Azure AI} backed by
 * {@link OpenAIClient}.
 *
 * @author Benoit Moussaud
 * @author Sebastien Deleuze
 * @see ImageModel
 * @see com.azure.ai.openai.OpenAIClient
 * @since 1.0.0
 */
public class AzureOpenAiImageModel implements ImageModel {

	private static final String DEFAULT_DEPLOYMENT_NAME = AzureOpenAiImageOptions.DEFAULT_IMAGE_MODEL;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final OpenAIClient openAIClient;

	private final AzureOpenAiImageOptions defaultOptions;

	private final JsonMapper jsonMapper;

	public AzureOpenAiImageModel(OpenAIClient openAIClient) {
		this(openAIClient, AzureOpenAiImageOptions.builder().deploymentName(DEFAULT_DEPLOYMENT_NAME).build());
	}

	public AzureOpenAiImageModel(OpenAIClient microsoftOpenAiClient, AzureOpenAiImageOptions options) {
		Assert.notNull(microsoftOpenAiClient, "com.azure.ai.openai.OpenAIClient must not be null");
		Assert.notNull(options, "AzureOpenAiChatOptions must not be null");
		this.openAIClient = microsoftOpenAiClient;
		this.defaultOptions = options;
		this.jsonMapper = JsonMapper.builder()
			.addModules(JacksonUtils.instantiateAvailableModules())
			.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
			.build();
	}

	public AzureOpenAiImageOptions getDefaultOptions() {
		return this.defaultOptions;
	}

	@Override
	public ImageResponse call(ImagePrompt imagePrompt) {
		ImageGenerationOptions imageGenerationOptions = toOpenAiImageOptions(imagePrompt);
		String deploymentOrModelName = getDeploymentName(imagePrompt);
		if (logger.isTraceEnabled()) {
			logger.trace("Azure ImageGenerationOptions call {} with the following options : {} ", deploymentOrModelName,
					toPrettyJson(imageGenerationOptions));
		}

		var images = this.openAIClient.getImageGenerations(deploymentOrModelName, imageGenerationOptions);

		if (logger.isTraceEnabled()) {
			logger.trace("Azure ImageGenerations: {}", toPrettyJson(images));
		}

		List<ImageGeneration> imageGenerations = images.getData().stream().map(entry -> {
			var image = new Image(entry.getUrl(), entry.getBase64Data());
			var metadata = new AzureOpenAiImageGenerationMetadata(entry.getRevisedPrompt());
			return new ImageGeneration(image, metadata);
		}).toList();

		ImageResponseMetadata openAiImageResponseMetadata = AzureOpenAiImageResponseMetadata.from(images);
		return new ImageResponse(imageGenerations, openAiImageResponseMetadata);
	}

	private String toPrettyJson(Object object) {
		try {
			return this.jsonMapper.writeValueAsString(object);
		}
		catch (JacksonException e) {
			return "JsonProcessingException:" + e + " [" + object.toString() + "]";
		}
	}

	/**
	 * Return the deployment-name if provided or use the model name.
	 * @param prompt the image prompt
	 * @return Return the deployment-name if provided or use the model name.
	 */
	private String getDeploymentName(ImagePrompt prompt) {
		var runtimeImageOptions = prompt.getOptions();

		if (this.defaultOptions != null) {
			// Merge options fixed in beta7
			// https://github.com/Azure/azure-sdk-for-java/issues/38183
			runtimeImageOptions = ModelOptionsUtils.merge(runtimeImageOptions, this.defaultOptions,
					AzureOpenAiImageOptions.class);
		}

		if (runtimeImageOptions != null) {
			if (runtimeImageOptions instanceof AzureOpenAiImageOptions runtimeAzureOpenAiImageOptions) {
				if (runtimeAzureOpenAiImageOptions.getDeploymentName() != null) {
					return runtimeAzureOpenAiImageOptions.getDeploymentName();
				}
			}

		}

		// By default the one provided in the image prompt
		return prompt.getOptions().getModel();

	}

	private ImageGenerationOptions toOpenAiImageOptions(ImagePrompt prompt) {

		if (prompt.getInstructions().size() > 1) {
			throw new RuntimeException(java.lang.String
				.format("implementation support 1 image instruction only, found %s", prompt.getInstructions().size()));
		}
		if (prompt.getInstructions().isEmpty()) {
			throw new RuntimeException("please provide image instruction, current is empty");
		}

		var instructions = prompt.getInstructions().get(0).getText();
		var runtimeImageOptions = prompt.getOptions();
		ImageGenerationOptions imageGenerationOptions = new ImageGenerationOptions(instructions);

		if (this.defaultOptions != null) {
			// Merge options fixed in beta7
			// https://github.com/Azure/azure-sdk-for-java/issues/38183
			runtimeImageOptions = ModelOptionsUtils.merge(runtimeImageOptions, this.defaultOptions,
					AzureOpenAiImageOptions.class);
		}

		if (runtimeImageOptions != null) {
			// Handle portable image options
			if (runtimeImageOptions.getN() != null) {
				imageGenerationOptions.setN(runtimeImageOptions.getN());
			}
			if (runtimeImageOptions.getModel() != null) {
				imageGenerationOptions.setModel(runtimeImageOptions.getModel());
			}
			if (runtimeImageOptions.getResponseFormat() != null) {
				// b64_json or url
				imageGenerationOptions.setResponseFormat(
						ImageGenerationResponseFormat.fromString(runtimeImageOptions.getResponseFormat()));
			}
			if (runtimeImageOptions.getWidth() != null && runtimeImageOptions.getHeight() != null) {
				imageGenerationOptions.setSize(
						ImageSize.fromString(runtimeImageOptions.getWidth() + "x" + runtimeImageOptions.getHeight()));
			}

			// Handle OpenAI specific image options
			if (runtimeImageOptions instanceof AzureOpenAiImageOptions runtimeAzureOpenAiImageOptions) {
				if (runtimeAzureOpenAiImageOptions.getQuality() != null) {
					imageGenerationOptions
						.setQuality(ImageGenerationQuality.fromString(runtimeAzureOpenAiImageOptions.getQuality()));
				}
				if (runtimeAzureOpenAiImageOptions.getStyle() != null) {
					imageGenerationOptions
						.setStyle(ImageGenerationStyle.fromString(runtimeAzureOpenAiImageOptions.getStyle()));
				}
				if (runtimeAzureOpenAiImageOptions.getUser() != null) {
					imageGenerationOptions.setUser(runtimeAzureOpenAiImageOptions.getUser());
				}
			}
		}
		return imageGenerationOptions;
	}

}
