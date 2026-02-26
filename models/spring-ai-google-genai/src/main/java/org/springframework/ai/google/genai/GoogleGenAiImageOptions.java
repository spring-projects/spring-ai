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

package org.springframework.ai.google.genai;

import org.springframework.ai.image.ImageOptions;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Options container for Gemini native image generation ("Nano Banana").
 *
 * <p>
 * This POJO models only API-level parameters described in:
 * https://ai.google.dev/gemini-api/docs/image-generation
 *
 * <p>
 * Supported models (as per requirement):
 * <ul>
 * <li>{@code gemini-2.5-flash-image}</li>
 * <li>{@code gemini-3-pro-image-preview}</li>
 * </ul>
 *
 * <p>
 * IMPORTANT: Some values (e.g., aspect ratio and image size) are intentionally
 * represented as {@link String} instead of enums to avoid limiting forward compatibility
 * if Google introduces new values. Enums/constants are provided only as suggestions
 * (autocomplete).
 *
 * @author Danil Temnikov
 */

public class GoogleGenAiImageOptions implements ImageOptions {

	/**
	 * Gemini image generation model identifier.
	 *
	 * <p>
	 * Recommended values:
	 * <ul>
	 * <li>{@link Models#GEMINI_2_5_FLASH_IMAGE}</li>
	 * <li>{@link Models#GEMINI_3_PRO_IMAGE_PREVIEW}</li>
	 * </ul>
	 */
	@Nullable
	private String model;

	/**
	 * Optional image output configuration.
	 *
	 * <p>
	 * Contains only parameters explicitly documented for image generation:
	 * <ul>
	 * <li>{@code aspectRatio} (both models)</li>
	 * <li>{@code imageSize} (Gemini 3 Pro Image Preview only)</li>
	 * </ul>
	 *
	 * <p>
	 * Keep {@code null} (or keep its fields null) to let the API use its defaults.
	 */
	@Nullable
	private ImageConfig imageConfig;

	public GoogleGenAiImageOptions() {
	}

	private GoogleGenAiImageOptions(Builder builder) {
		this.model = builder.model;
		this.imageConfig = builder.imageConfig;
	}

	// ---------------------------------------------------------------------
	// Default factory methods
	// ---------------------------------------------------------------------

	/**
	 * Creates default options using the cheapest/simplest supported model.
	 *
	 * <p>
	 * Delegates to {@link #defaults(String)} with {@link Models#GEMINI_2_5_FLASH_IMAGE}.
	 */
	public static GoogleGenAiImageOptions defaults() {
		return defaults(Models.GEMINI_2_5_FLASH_IMAGE);
	}

	/**
	 * Creates model-aware default options.
	 *
	 * <p>
	 * Goal: "simple and cheap" defaults, while remaining valid for each model. If a
	 * parameter can be omitted (null) and the API still works, it is kept null.
	 *
	 * <p>
	 * Behavior:
	 * <ul>
	 * <li>{@code gemini-2.5-flash-image}: leave {@code imageConfig} null (API defaults
	 * apply).</li>
	 * <li>{@code gemini-3-pro-image-preview}: set {@code imageConfig.imageSize="1K"} to
	 * keep output minimal; leave {@code aspectRatio} null (API defaults apply).</li>
	 * </ul>
	 */
	public static GoogleGenAiImageOptions defaults(String model) {
		Objects.requireNonNull(model, "model must not be null");

		GoogleGenAiImageOptions options = new GoogleGenAiImageOptions();
		options.setModel(model);

		if (Models.GEMINI_3_PRO_IMAGE_PREVIEW.equals(model)) {
			ImageConfig cfg = new ImageConfig();
			// Lowest documented size for Gemini 3 Pro Image Preview.
			cfg.setImageSize(ImageSizes.SIZE_1K);
			// Keep null to let API default to input dims / 1:1 when no input image.
			cfg.setAspectRatio(null);
			options.setImageConfig(cfg);
		} else {
			options.setImageConfig(new ImageConfig());
		}

		return options;
	}

	/**
	 * A reusable default instance for the cheapest model.
	 *
	 * <p>
	 * Warning: this instance is mutable. Prefer {@link #defaults()} for per-request
	 * customization.
	 */
	public static final GoogleGenAiImageOptions DEFAULTS = GoogleGenAiImageOptions
			.defaults(Models.GEMINI_2_5_FLASH_IMAGE);

	/**
	 * A reusable default instance for Gemini 3 Pro Image Preview (minimal size).
	 *
	 * <p>
	 * Warning: this instance is mutable. Prefer {@link #defaults(String)} for per-request
	 * customization.
	 */
	public static final GoogleGenAiImageOptions DEFAULTS_PRO_PREVIEW = GoogleGenAiImageOptions
			.defaults(Models.GEMINI_3_PRO_IMAGE_PREVIEW);

	// ---------------------------------------------------------------------
	// ImageOptions interface
	// ---------------------------------------------------------------------

	@Override
	public @Nullable Integer getN() {
		return 1;
	}

	@Override
	public @Nullable String getModel() {
		return this.model;
	}

	/**
	 * Derived output width in pixels, if resolvable.
	 *
	 * <p>
	 * <b>Note:</b> Width is not a direct API parameter in the referenced documentation.
	 * The Gemini API controls resolution via aspect ratio and (for Gemini 3) image size.
	 * This getter derives width from the documented resolution tables only for known
	 * values.
	 *
	 * <p>
	 * If {@code aspectRatio} / {@code imageSize} is unknown (e.g., new values introduced
	 * later), returns {@code null}.
	 */
	@Override
	public @Nullable Integer getWidth() {
		return resolveResolution().width;
	}

	/**
	 * Derived output height in pixels, if resolvable.
	 *
	 * <p>
	 * See {@link #getWidth()} for derivation caveats.
	 */
	@Override
	public @Nullable Integer getHeight() {
		return resolveResolution().height;
	}

	/**
	 * Response format is not configurable for Gemini image generation in the referenced
	 * documentation.
	 */
	@Override
	public @Nullable String getResponseFormat() {
		return null;
	}

	/**
	 * Style is prompt-driven and not represented as an API parameter.
	 */
	@Override
	public @Nullable String getStyle() {
		return null;
	}

	// ---------------------------------------------------------------------
	// Accessors
	// ---------------------------------------------------------------------

	public void setModel(@Nullable String model) {
		this.model = model;
	}

	public @Nullable ImageConfig getImageConfig() {
		return imageConfig;
	}

	public void setImageConfig(@Nullable ImageConfig imageConfig) {
		this.imageConfig = imageConfig;
	}

	// ---------------------------------------------------------------------
	// Resolution derivation (best-effort for known values only)
	// ---------------------------------------------------------------------

	private Resolution resolveResolution() {
		if (imageConfig == null) {
			return Resolution.NULL;
		}

		final String ar = imageConfig.getAspectRatio();
		final String m = this.model;

		if (ar == null || m == null) {
			return Resolution.NULL;
		}

		// Gemini 2.5 Flash Image: fixed resolution per aspect ratio (known table values).
		if (Models.GEMINI_2_5_FLASH_IMAGE.equals(m)) {
			return Resolution.forGemini25Flash(ar);
		}

		// Gemini 3 Pro Image Preview: depends on aspect ratio + image size (known table
		// values).
		if (Models.GEMINI_3_PRO_IMAGE_PREVIEW.equals(m)) {
			final String size = imageConfig.getImageSize();
			if (size == null) {
				return Resolution.NULL;
			}
			return Resolution.forGemini3Pro(ar, size);
		}

		return Resolution.NULL;
	}

	private static final class Resolution {

		static final Resolution NULL = new Resolution(null, null);

		final Integer width;

		final Integer height;

		private Resolution(Integer width, Integer height) {
			this.width = width;
			this.height = height;
		}

		static Resolution forGemini25Flash(String aspectRatio) {
			switch (aspectRatio) {
				case AspectRatios.RATIO_1_1:
					return new Resolution(1024, 1024);
				case AspectRatios.RATIO_2_3:
					return new Resolution(832, 1248);
				case AspectRatios.RATIO_3_2:
					return new Resolution(1248, 832);
				case AspectRatios.RATIO_3_4:
					return new Resolution(864, 1184);
				case AspectRatios.RATIO_4_3:
					return new Resolution(1184, 864);
				case AspectRatios.RATIO_4_5:
					return new Resolution(896, 1152);
				case AspectRatios.RATIO_5_4:
					return new Resolution(1152, 896);
				case AspectRatios.RATIO_9_16:
					return new Resolution(768, 1344);
				case AspectRatios.RATIO_16_9:
					return new Resolution(1344, 768);
				case AspectRatios.RATIO_21_9:
					return new Resolution(1536, 672);
				default:
					return NULL;
			}
		}

		static Resolution forGemini3Pro(String aspectRatio, String imageSize) {
			switch (imageSize) {
				case ImageSizes.SIZE_1K:
					switch (aspectRatio) {
						case AspectRatios.RATIO_1_1:
							return new Resolution(1024, 1024);
						case AspectRatios.RATIO_2_3:
							return new Resolution(848, 1264);
						case AspectRatios.RATIO_3_2:
							return new Resolution(1264, 848);
						case AspectRatios.RATIO_3_4:
							return new Resolution(896, 1200);
						case AspectRatios.RATIO_4_3:
							return new Resolution(1200, 896);
						case AspectRatios.RATIO_4_5:
							return new Resolution(928, 1152);
						case AspectRatios.RATIO_5_4:
							return new Resolution(1152, 928);
						case AspectRatios.RATIO_9_16:
							return new Resolution(768, 1376);
						default:
							return NULL;
					}
				case ImageSizes.SIZE_2K:
					switch (aspectRatio) {
						case AspectRatios.RATIO_1_1:
							return new Resolution(2048, 2048);
						case AspectRatios.RATIO_2_3:
							return new Resolution(1696, 2528);
						case AspectRatios.RATIO_3_2:
							return new Resolution(2528, 1696);
						case AspectRatios.RATIO_3_4:
							return new Resolution(1792, 2400);
						case AspectRatios.RATIO_4_3:
							return new Resolution(2400, 1792);
						case AspectRatios.RATIO_4_5:
							return new Resolution(1856, 2304);
						case AspectRatios.RATIO_5_4:
							return new Resolution(2304, 1856);
						case AspectRatios.RATIO_9_16:
							return new Resolution(1536, 2752);
						default:
							return NULL;
					}
				case ImageSizes.SIZE_4K:
					switch (aspectRatio) {
						case AspectRatios.RATIO_1_1:
							return new Resolution(4096, 4096);
						case AspectRatios.RATIO_2_3:
							return new Resolution(3392, 5056);
						case AspectRatios.RATIO_3_2:
							return new Resolution(5056, 3392);
						case AspectRatios.RATIO_3_4:
							return new Resolution(3584, 4800);
						case AspectRatios.RATIO_4_3:
							return new Resolution(4800, 3584);
						case AspectRatios.RATIO_4_5:
							return new Resolution(3712, 4608);
						case AspectRatios.RATIO_5_4:
							return new Resolution(4608, 3712);
						case AspectRatios.RATIO_9_16:
							return new Resolution(3072, 5504);
						default:
							return NULL;
					}
				default:
					return NULL;
			}
		}

	}

	// ---------------------------------------------------------------------
	// Nested types / suggestion constants
	// ---------------------------------------------------------------------

	/**
	 * Known model ids (as strings) for autocomplete without restricting forward
	 * compatibility.
	 */
	public static final class Models {

		/**
		 * {@code gemini-2.5-flash-image}
		 */
		public static final String GEMINI_2_5_FLASH_IMAGE = "gemini-2.5-flash-image";

		/**
		 * {@code gemini-3-pro-image-preview}
		 */
		public static final String GEMINI_3_PRO_IMAGE_PREVIEW = "gemini-3-pro-image-preview";

		private Models() {
		}

	}

	/**
	 * ImageConfig contains optional image-output controls documented for image
	 * generation.
	 *
	 * <p>
	 * IMPORTANT: {@code aspectRatio} and {@code imageSize} are strings to avoid future
	 * breakage if new values appear in the API.
	 */
	public static final class ImageConfig {

		/**
		 * Desired output aspect ratio (wire value, e.g., {@code "16:9"}).
		 *
		 * <p>
		 * Recommended known values are available in {@link AspectRatios} for
		 * autocomplete.
		 * <p>
		 * Keep {@code null} to let the API use its default behavior.
		 */
		@Nullable
		private String aspectRatio;

		/**
		 * Output image size (wire value, e.g., {@code "1K"}, {@code "2K"}, {@code "4K"}).
		 *
		 * <p>
		 * <b>Limitation:</b> Documented only for {@code gemini-3-pro-image-preview}.
		 * Recommended known values are available in {@link ImageSizes} for autocomplete.
		 *
		 * <p>
		 * Keep {@code null} to let the API use its default behavior.
		 */
		@Nullable
		private String imageSize;

		public ImageConfig() {
		}

		public ImageConfig(@Nullable String aspectRatio, @Nullable String imageSize) {
			this.aspectRatio = aspectRatio;
			this.imageSize = imageSize;
		}

		public @Nullable String getAspectRatio() {
			return aspectRatio;
		}

		public void setAspectRatio(@Nullable String aspectRatio) {
			this.aspectRatio = aspectRatio;
		}

		public @Nullable String getImageSize() {
			return imageSize;
		}

		public void setImageSize(@Nullable String imageSize) {
			this.imageSize = imageSize;
		}

		/**
		 * Returns {@code true} if both fields are {@code null}.
		 */
		public boolean isEmpty() {
			return aspectRatio == null && imageSize == null;
		}

		/**
		 * Creates a defensive copy of the given config, or {@code null} if {@code input}
		 * is null.
		 */
		public static @Nullable ImageConfig copyOf(@Nullable ImageConfig input) {
			if (input == null) {
				return null;
			}
			return new ImageConfig(input.aspectRatio, input.imageSize);
		}

		/**
		 * Builder entry point.
		 */
		public static Builder builder() {
			return new Builder();
		}

		/**
		 * Builder for {@link ImageConfig}.
		 */
		public static final class Builder {

			@Nullable
			private String aspectRatio;

			@Nullable
			private String imageSize;

			private Builder() {
			}

			/**
			 * Sets aspect ratio (wire value, e.g. {@code "16:9"}).
			 *
			 * <p>
			 * Suggested values: {@link AspectRatios}
			 */
			public Builder aspectRatio(@Nullable String aspectRatio) {
				this.aspectRatio = aspectRatio;
				return this;
			}

			/**
			 * Sets image size (wire value, e.g. {@code "1K"}).
			 *
			 * <p>
			 * Suggested values: {@link ImageSizes}
			 *
			 * <p>
			 * Note: size is currently documented only for gemini-3-pro-image-preview.
			 */
			public Builder imageSize(@Nullable String imageSize) {
				this.imageSize = imageSize;
				return this;
			}

			/**
			 * Builds an immutable {@link ImageConfig}.
			 */
			public ImageConfig build() {
				return new ImageConfig(aspectRatio, imageSize);
			}

		}

		@Override
		public String toString() {
			return "ImageConfig{" + "aspectRatio='" + aspectRatio + '\'' + ", imageSize='" + imageSize + '\'' + '}';
		}

	}

	/**
	 * Suggested aspect ratio wire values from the documentation tables.
	 *
	 * <p>
	 * These are hints only. The API may accept additional ratios in the future.
	 */
	public static final class AspectRatios {

		public static final String RATIO_1_1 = "1:1";

		public static final String RATIO_2_3 = "2:3";

		public static final String RATIO_3_2 = "3:2";

		public static final String RATIO_3_4 = "3:4";

		public static final String RATIO_4_3 = "4:3";

		public static final String RATIO_4_5 = "4:5";

		public static final String RATIO_5_4 = "5:4";

		public static final String RATIO_9_16 = "9:16";

		public static final String RATIO_16_9 = "16:9";

		public static final String RATIO_21_9 = "21:9";

		private AspectRatios() {
		}

	}

	/**
	 * Suggested image size wire values from the documentation for Gemini 3 Pro Image
	 * Preview.
	 *
	 * <p>
	 * These are hints only. The API may accept additional sizes in the future.
	 */
	public static final class ImageSizes {

		public static final String SIZE_1K = "1K";

		public static final String SIZE_2K = "2K";

		public static final String SIZE_4K = "4K";

		private ImageSizes() {
		}

	}

	// ---------------------------------------------------------------------
	// Builder
	// ---------------------------------------------------------------------

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private String model;

		private ImageConfig imageConfig;

		private Builder() {
		}

		/**
		 * Sets model id.
		 */
		public Builder model(String model) {
			this.model = model;
			return this;
		}

		/**
		 * Sets image config. Keep null to use API defaults.
		 */
		public Builder imageConfig(ImageConfig imageConfig) {
			this.imageConfig = imageConfig;
			return this;
		}

		public GoogleGenAiImageOptions build() {
			GoogleGenAiImageOptions o = new GoogleGenAiImageOptions();
			o.model = this.model;
			o.imageConfig = this.imageConfig;
			return o;
		}

	}

}
