package org.springframework.ai.azure.openai.metadata;

import com.azure.ai.openai.models.ImageGenerations;
import org.springframework.ai.image.ImageResponseMetadata;
import org.springframework.ai.model.MutableResponseMetadata;
import org.springframework.util.Assert;

import java.util.HashMap;
import java.util.Objects;

/**
 * Represents metadata associated with an image response from the Azure OpenAI image
 * model. It provides additional information about the generative response from the Azure
 * OpenAI image model, including the creation timestamp of the generated image.
 *
 * @author Benoit Moussaud
 * @since 1.0.0 M1
 */
public class AzureOpenAiImageResponseMetadata extends ImageResponseMetadata {

	private final Long created;

	public static AzureOpenAiImageResponseMetadata from(ImageGenerations openAiImageResponse) {
		Assert.notNull(openAiImageResponse, "OpenAiImageResponse must not be null");
		return new AzureOpenAiImageResponseMetadata(openAiImageResponse.getCreatedAt().toEpochSecond());
	}

	protected AzureOpenAiImageResponseMetadata(Long created) {
		this.created = created;
	}

	@Override
	public Long getCreated() {
		return this.created;
	}

	@Override
	public String toString() {
		return "AzureOpenAiImageResponseMetadata{" + "created=" + created + '}';
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof AzureOpenAiImageResponseMetadata that))
			return false;
		return Objects.equals(created, that.created);
	}

	@Override
	public int hashCode() {
		return Objects.hash(created);
	}

}
