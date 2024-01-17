package org.springframework.ai.image;

import org.springframework.ai.model.ModelCall;

@FunctionalInterface
public interface ImageClient extends ModelCall<ImagePrompt, ImageResponse> {

	ImageResponse call(ImagePrompt request);

}
