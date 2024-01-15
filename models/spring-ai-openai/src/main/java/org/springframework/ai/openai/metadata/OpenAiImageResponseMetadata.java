package org.springframework.ai.openai.metadata;

import org.springframework.ai.image.ImageResponseMetadata;
import org.springframework.ai.openai.api.OpenAiImageApi;
import org.springframework.util.Assert;

import java.util.Objects;

public class OpenAiImageResponseMetadata implements ImageResponseMetadata {

	private final Long created;

	public static OpenAiImageResponseMetadata from(OpenAiImageApi.OpenAiImageResponse openAiImageResponse) {
		Assert.notNull(openAiImageResponse, "OpenAiImageResponse must not be null");
		return new OpenAiImageResponseMetadata(openAiImageResponse.created());
	}

	protected OpenAiImageResponseMetadata(Long created) {
		this.created = created;
	}

	@Override
	public Long created() {
		return this.created;
	}

	@Override
	public String toString() {
		return "OpenAiImageResponseMetadata{" + "created=" + created + '}';
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof OpenAiImageResponseMetadata that))
			return false;
		return Objects.equals(created, that.created);
	}

	@Override
	public int hashCode() {
		return Objects.hash(created);
	}

}
