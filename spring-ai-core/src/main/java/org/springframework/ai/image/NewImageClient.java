package org.springframework.ai.image;

import org.springframework.ai.model.ModelClient;

import java.util.List;

@FunctionalInterface
public interface NewImageClient extends ModelClient<ImagePrompt, ImageResponse> {

	ImageResponse call(ImagePrompt request);

}
