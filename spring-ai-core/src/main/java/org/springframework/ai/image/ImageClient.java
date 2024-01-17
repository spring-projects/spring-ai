package org.springframework.ai.image;

import org.springframework.ai.model.ModelClient;

@FunctionalInterface
public interface ImageClient extends ModelClient<ImagePrompt, ImageResponse> {

	ImageResponse call(ImagePrompt request);

}
