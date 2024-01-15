package org.springframework.ai.image;

import org.springframework.ai.model.ModelResponse;
import org.springframework.ai.model.ResponseMetadata;

import java.util.List;
import java.util.Objects;

public class ImageResponse implements ModelResponse<ImageGeneration> {

	private final ImageResponseMetadata imageResponseMetadata;

	private final List<ImageGeneration> imageGenerations;

	public ImageResponse(List<ImageGeneration> generations) {
		this(generations, ImageResponseMetadata.NULL);
	}

	public ImageResponse(List<ImageGeneration> generations, ImageResponseMetadata imageResponseMetadata) {
		this.imageResponseMetadata = imageResponseMetadata;
		this.imageGenerations = List.copyOf(generations);
	}

	@Override
	public ImageGeneration getResult() {
		return imageGenerations.get(0);
	}

	@Override
	public List<ImageGeneration> getResults() {
		return imageGenerations;
	}

	@Override
	public ImageResponseMetadata getMetadata() {
		return imageResponseMetadata;
	}

	@Override
	public String toString() {
		return "ImageResponse{" + "imageResponseMetadata=" + imageResponseMetadata + ", imageGenerations="
				+ imageGenerations + '}';
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof ImageResponse that))
			return false;
		return Objects.equals(imageResponseMetadata, that.imageResponseMetadata)
				&& Objects.equals(imageGenerations, that.imageGenerations);
	}

	@Override
	public int hashCode() {
		return Objects.hash(imageResponseMetadata, imageGenerations);
	}

}
