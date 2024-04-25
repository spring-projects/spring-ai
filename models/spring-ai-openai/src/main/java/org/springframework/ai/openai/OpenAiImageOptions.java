package org.springframework.ai.openai;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.ai.image.ImageOptions;

/**
 * OpenAI Image API options. OpenAiImageOptions.java
 *
 * @author Mark Pollack
 * @author Christian Tzolov
 * @since 0.8.0
 */
public interface OpenAiImageOptions extends ImageOptions {

	/**
	 * The quality of the image that will be generated. hd creates images with finer
	 * details and greater consistency across the image. This param is only supported for
	 * dall-e-3.
	 */
	@JsonProperty("quality")
	String getQuality();

	/**
	 * The style of the generated images. Must be one of vivid or natural. Vivid causes
	 * the model to lean towards generating hyper-real and dramatic images. Natural causes
	 * the model to produce more natural, less hyper-real looking images. This param is
	 * only supported for dall-e-3.
	 */
	@JsonProperty("style")
	String getStyle();

	/**
	 * A unique identifier representing your end-user, which can help OpenAI to monitor
	 * and detect abuse.
	 */
	@JsonProperty("user")
	String getUser();

	/**
	 * The size of the generated images. Must be one of 256x256, 512x512, or 1024x1024 for
	 * dall-e-2. Must be one of 1024x1024, 1792x1024, or 1024x1792 for dall-e-3 models.
	 */
	@JsonProperty("size")
	String getSize();

}
