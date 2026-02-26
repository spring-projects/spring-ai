/*
 * Copyright 2025-2026 the original author or authors.
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
package org.springframework.ai.vertexai.imagen;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.ai.image.ImageOptions;

import java.util.List;

import static org.springframework.ai.vertexai.imagen.VertexAiImagenUtils.calculateSizeFromAspectRatio;

/**
 * <h4>Options for the Vertex AI Image service.</h4>
 *
 * @author Sami Marzouki
 */
public class VertexAiImagenImageOptions implements ImageOptions {

	public static final String DEFAULT_MODEL_NAME = VertexAiImagenImageModelName.IMAGEN_3_V002.getValue();

	/**
	 * <b>Required: int</b>
	 * <p>
	 * The number of images to generate. The default value is 4.
	 * </p>
	 * <ul>
	 * <li>imagen-3.0-generate-001 model supports values 1 through 4.</li>
	 * <li>imagen-3.0-fast-generate-001 model supports values 1 through 4.</li>
	 * <li>imagegeneration@006 model supports values 1 through 4.</li>
	 * <li>imagegeneration@005 model supports values 1 through 4.</li>
	 * <li>imagegeneration@002 model supports values 1 through 8.</li>
	 * </ul>
	 */
	@JsonProperty("sampleCount")
	private Integer n;

	/**
	 * <p>
	 * The model to use for image generation.
	 * </p>
	 */
	@JsonProperty("model")
	private String model;

	/**
	 * <b>Optional: Uint32</b>
	 * <p>
	 * The random seed for image generation. This is not available when addWatermark is
	 * set to true.
	 * </p>
	 */
	@JsonProperty("seed")
	private Integer seed;

	/**
	 * <b>Optional: string</b>
	 * <p>
	 * A description of what to discourage in the generated images.
	 * <p>
	 * <ul>
	 * <li>The imagen-3.0-generate-001 model supports up to 480 tokens.</li>
	 * <li>The imagen-3.0-fast-generate-001 model supports up to 480 tokens.</li>
	 * <li>The imagegeneration@006 model supports up to 128 tokens.</li>
	 * <li>The imagegeneration@005 model supports up to 128 tokens.</li>
	 * <li>The imagegeneration@002 model supports up to 64 tokens.</li>
	 * </ul>
	 * negativePrompt isn't supported by imagen-3.0-generate-002 and newer models.
	 */
	@JsonProperty("negativePrompt")
	private String negativePrompt;

	/**
	 * <b>Optional: string</b>
	 * <p>
	 * Specifies the generated image's output resolution.<br>
	 * The accepted values are "1K" or "2K".<br>
	 * The default value is "1K".
	 * </p>
	 */
	@JsonProperty("sampleImageSize")
	private String sampleImageSize;

	/**
	 * <b>Optional: boolean</b>
	 * <p>
	 * An optional parameter to use an LLM-based prompt rewriting feature to deliver
	 * higher quality images that better reflect the original prompt's intent. Disabling
	 * this feature may impact image quality and prompt adherence.
	 * </p>
	 */
	@JsonProperty("enhancePrompt")
	private Boolean enhancePrompt;

	/**
	 * <b>Optional: string</b>
	 * <p>
	 * The language code that corresponds to your text prompt language.
	 * </p>
	 * <ul>
	 * <li>auto: Automatic detection.
	 * <p>
	 * If Imagen detects a supported language, the prompt and an optional negative prompt
	 * are translated to English. If the language detected isn't supported, Imagen uses
	 * the input text verbatim, which might result in an unexpected output. No error code
	 * is returned.
	 * </p>
	 * </li>
	 * <li>en: English (if omitted, the default value)</li>
	 * <li>zh or zh-CN: Chinese (simplified)</li>
	 * <li>zh-TW: Chinese (traditional)</li>
	 * <li>hi: Hindi</li>
	 * <li>ja: Japanese</li>
	 * <li>ko: Korean</li>
	 * <li>pt: Portuguese</li>
	 * <li>es: Spanish</li>
	 * </ul>
	 */
	@JsonProperty("language")
	private String language;

	/**
	 * </b>Optional: string</b>
	 * <p>
	 * The aspect ratio for the image. The default value is "1:1".
	 * </p>
	 * <ul>
	 * <li>The imagen-3.0-generate-002 model supports "1:1", "9:16", "16:9", "3:4", or
	 * "4:3".</li>
	 * <li>The imagen-3.0-generate-001 model supports "1:1", "9:16", "16:9", "3:4", or
	 * "4:3".</li>
	 * <li>The imagen-3.0-fast-generate-001 model supports "1:1", "9:16", "16:9", "3:4",
	 * or "4:3".</li>
	 * <li>The imagegeneration@006 model supports "1:1", "9:16", "16:9", "3:4", or
	 * "4:3".</li>
	 * <li>The imagegeneration@005 model supports "1:1" or "9:16".</li>
	 * <li>The imagegeneration@002 model supports "1:1".</li>
	 * </ul>
	 */
	@JsonProperty("aspectRatio")
	private String aspectRatio;

	/**
	 * <b>Optional: outputOptions</b>
	 * <p>
	 * Describes the output image format in an outputOptions object.
	 * </p>
	 *
	 * @see OutputOptions
	 */
	@JsonProperty("outputOptions")
	private OutputOptions outputOptions;

	/**
	 * <b>Optional: string (imagegeneration@002 only)</b>
	 * <p>
	 * Describes the style for the generated images. The following values are supported:
	 * </p>
	 * <ul>
	 * <li>"photograph"</li>
	 * <li>"digital_art"</li>
	 * <li>"landscape"</li>
	 * <li>"sketch"</li>
	 * <li>"watercolor"</li>
	 * <li>"cyberpunk"</li>
	 * <li>"pop_art"</li>
	 * </ul>
	 */
	@JsonProperty("sampleImageStyle")
	private String style;

	/**
	 * <b>Optional: string (imagen-3.0-generate-001, imagen-3.0-fast-generate-001, and
	 * imagegeneration@006 only)</b>
	 * <p>
	 * Allow generation of people by the model. The following values are supported:
	 * </p>
	 * <ul>
	 * <li>"dont_allow": Disallow the inclusion of people or faces in images.</li>
	 * <li>"allow_adult": Allow generation of adults only.</li>
	 * <li>"allow_all": Allow generation of people of all ages.</li>
	 * </ul>
	 * <p>
	 * The default value is "allow_adult".
	 * </p>
	 */
	@JsonProperty("personGeneration")
	private String personGeneration;

	/**
	 * <b>Optional: string (imagen-3.0-generate-001, imagen-3.0-fast-generate-001, and
	 * imagegeneration@006 only)</b>
	 * <p>
	 * Adds a filter level to safety filtering. The following values are supported:
	 * </p>
	 * <ul>
	 * <li>"block_low_and_above": Strongest filtering level, most strict blocking.<br>
	 * Deprecated value: "block_most".</li>
	 * <li>"block_medium_and_above": Block some problematic prompts and responses.<br>
	 * Deprecated value: "block_some".</li>
	 * <li>"block_only_high": Reduces the number of requests blocked due to safety
	 * filters. May increase objectionable content generated by Imagen.<br>
	 * Deprecated value: "block_few".</li>
	 * <li>"block_none": Block very few problematic prompts and responses. Access to this
	 * feature is restricted. <br>
	 * Previous field value: "block_fewest".</li>
	 * </ul>
	 * <p>
	 * The default value is "block_medium_and_above".
	 * </p>
	 */
	@JsonProperty("safetySetting")
	private String safetySetting;

	/**
	 * <b>Optional: bool</b>
	 * <p>
	 * Add an invisible watermark to the generated images. The default value is false for
	 * the imagegeneration@002 and imagegeneration@005 models, and true for the
	 * imagen-3.0-fast-generate-001, imagegeneration@006, and imagegeneration@006 models.
	 * </p>
	 */
	@JsonProperty("addWatermark")
	private Boolean addWatermark;

	/**
	 * <b>Optional: string</b>
	 * <p>
	 * Cloud Storage URI to store the generated images.
	 * </p>
	 */
	@JsonProperty("storageUri")
	private String storageUri;

	private List<Integer> size;

	public static Builder builder() {
		return new Builder();
	}

	@Override
	public Integer getN() {
		return this.n;
	}

	public void setN(Integer n) {
		this.n = n;
	}

	@Override
	public String getModel() {
		return this.model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	@Override
	public Integer getWidth() {
		if (this.size == null || this.size.isEmpty()) {
			return null;
		}
		return this.size.get(0);
	}

	@Override
	public Integer getHeight() {
		if (this.size == null || this.size.isEmpty()) {
			return null;
		}
		return this.size.get(1);
	}

	@Override
	public String getStyle() {
		return this.style;
	}

	public void setStyle(String style) {
		this.style = style;
	}

	@Override
	public String getResponseFormat() {
		if (this.outputOptions == null) {
			return null;
		}
		return this.outputOptions.mimeType;
	}

	public Integer getCompressionQuality() {
		if (this.outputOptions == null) {
			return null;
		}
		return this.outputOptions.compressionQuality;
	}

	public OutputOptions getOutputOptions() {
		return this.outputOptions;
	}

	public Integer getSeed() {
		return seed;
	}

	public void setSeed(Integer seed) {
		this.seed = seed;
	}

	public String getNegativePrompt() {
		return negativePrompt;
	}

	public void setNegativePrompt(String negativePrompt) {
		this.negativePrompt = negativePrompt;
	}

	public String getAspectRatio() {
		return aspectRatio;
	}

	public void setAspectRatio(String aspectRatio) {
		this.aspectRatio = aspectRatio;
	}

	public String getPersonGeneration() {
		return personGeneration;
	}

	public void setPersonGeneration(String personGeneration) {
		this.personGeneration = personGeneration;
	}

	public String getSafetySetting() {
		return safetySetting;
	}

	public void setSafetySetting(String safetySetting) {
		this.safetySetting = safetySetting;
	}

	public Boolean getAddWatermark() {
		return addWatermark;
	}

	public void setAddWatermark(Boolean addWatermark) {
		this.addWatermark = addWatermark;
	}

	public String getStorageUri() {
		return storageUri;
	}

	public void setStorageUri(String storageUri) {
		this.storageUri = storageUri;
	}

	public void setSize(List<Integer> size) {
		this.size = size;
	}

	public Boolean getEnhancePrompt() {
		return enhancePrompt;
	}

	public void setEnhancePrompt(Boolean enhancePrompt) {
		this.enhancePrompt = enhancePrompt;
	}

	public String getLanguage() {
		return language;
	}

	public void setLanguage(String language) {
		this.language = language;
	}

	public String getSampleImageSize() {
		return sampleImageSize;
	}

	public void setSampleImageSize(String sampleImageSize) {
		this.sampleImageSize = sampleImageSize;
	}

	public static final class OutputOptions {

		/**
		 * <b>Optional: string</b>
		 * <p>
		 * The image format that the output should be saved as. The following values are
		 * supported:
		 * </p>
		 * <ul>
		 * <li>"image/png": Save as a PNG image</li>
		 * <li>"image/jpeg": Save as a JPEG image</li>
		 * </ul>
		 * <p>
		 * The default value is "image/png".
		 * </p>
		 */
		@JsonProperty("mimeType")
		private String mimeType;

		/**
		 * <b>Optional: int</b>
		 * <p>
		 * The level of compression if the output type is "image/jpeg". Accepted values
		 * are 0 through 100. The default value is 75.
		 * </p>
		 */
		@JsonProperty("compressionQuality")
		private Integer compressionQuality;

		public static Builder builder() {
			return new Builder();
		}

		public String getMimeType() {
			return mimeType;
		}

		public void setMimeType(String mimeType) {
			this.mimeType = mimeType;
		}

		public Integer getCompressionQuality() {
			return compressionQuality;
		}

		public void setCompressionQuality(Integer compressionQuality) {
			this.compressionQuality = compressionQuality;
		}

		public static final class Builder {

			private final OutputOptions options;

			private Builder() {
				this.options = new OutputOptions();
			}

			public Builder mimeType(String format) {
				this.options.setMimeType(format);
				return this;
			}

			public Builder compressionQuality(Integer compressionQuality) {
				this.options.setCompressionQuality(compressionQuality);
				return this;
			}

			public OutputOptions build() {
				return this.options;
			}

		}

	}

	public static final class Builder {

		private final VertexAiImagenImageOptions options;

		private Builder() {
			this.options = new VertexAiImagenImageOptions();
		}

		public Builder from(VertexAiImagenImageOptions fromOptions) {
			if (fromOptions.getN() != null) {
				this.options.setN(fromOptions.getN());
			}
			if (fromOptions.getModel() != null) {
				this.options.setModel(fromOptions.getModel());
			}
			if (fromOptions.getAspectRatio() != null) {
				this.options.setAspectRatio(fromOptions.getAspectRatio());
				this.options.setSize(calculateSizeFromAspectRatio(fromOptions.getAspectRatio()));
			}
			if (fromOptions.getStyle() != null) {
				this.options.setStyle(fromOptions.getStyle());
			}
			if (fromOptions.getOutputOptions() != null) {
				if (fromOptions.getResponseFormat() != null) {
					this.options.outputOptions.setMimeType(fromOptions.getResponseFormat());
				}
				if (fromOptions.getCompressionQuality() != null) {
					this.options.outputOptions.setCompressionQuality(fromOptions.getCompressionQuality());
				}
			}
			if (fromOptions.getSeed() != null) {
				this.options.setSeed(fromOptions.getSeed());
			}
			if (fromOptions.getNegativePrompt() != null) {
				this.options.setNegativePrompt(fromOptions.getNegativePrompt());
			}
			if (fromOptions.getPersonGeneration() != null) {
				this.options.setPersonGeneration(fromOptions.getPersonGeneration());
			}
			if (fromOptions.getSafetySetting() != null) {
				this.options.setSafetySetting(fromOptions.getSafetySetting());
			}
			if (fromOptions.getAddWatermark() != null) {
				this.options.setAddWatermark(fromOptions.getAddWatermark());
			}
			if (fromOptions.getStorageUri() != null) {
				this.options.setStorageUri(fromOptions.getStorageUri());
			}
			if (fromOptions.getLanguage() != null) {
				this.options.setLanguage(fromOptions.getLanguage());
			}
			if (fromOptions.getEnhancePrompt() != null) {
				this.options.setEnhancePrompt(fromOptions.getEnhancePrompt());
			}
			if (fromOptions.getSampleImageSize() != null) {
				this.options.setSampleImageSize(fromOptions.getSampleImageSize());
			}

			return this;
		}

		public Builder N(Integer n) {
			this.options.setN(n);
			return this;
		}

		public Builder model(String model) {
			this.options.setModel(model);
			return this;
		}

		public Builder seed(Integer seed) {
			this.options.setSeed(seed);
			return this;
		}

		public Builder negativePrompt(String negativePrompt) {
			this.options.setNegativePrompt(negativePrompt);
			return this;
		}

		public Builder aspectRatio(String aspectRatio) {
			this.options.setAspectRatio(aspectRatio);
			this.options.setSize(calculateSizeFromAspectRatio(aspectRatio));
			return this;
		}

		public Builder outputOptions(OutputOptions outputOptions) {
			this.options.outputOptions = outputOptions;
			return this;
		}

		public Builder personGeneration(String personGeneration) {
			this.options.setPersonGeneration(personGeneration);
			return this;
		}

		public Builder safetySetting(String safetySetting) {
			this.options.setSafetySetting(safetySetting);
			return this;
		}

		public Builder addWatermark(Boolean addWatermark) {
			this.options.setAddWatermark(addWatermark);
			return this;
		}

		public Builder storageUri(String storageUri) {
			this.options.setStorageUri(storageUri);
			return this;
		}

		public Builder style(String style) {
			this.options.setStyle(style);
			return this;
		}

		public Builder language(String language) {
			this.options.setLanguage(language);
			return this;
		}

		public Builder enhancePrompt(Boolean enhancePrompt) {
			this.options.setEnhancePrompt(enhancePrompt);
			return this;
		}

		public Builder sampleImageSize(String sampleImageSize) {
			this.options.setSampleImageSize(sampleImageSize);
			return this;
		}

		public VertexAiImagenImageOptions build() {
			return this.options;
		}

	}

}
