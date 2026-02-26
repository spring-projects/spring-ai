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

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.google.protobuf.util.JsonFormat;
import org.springframework.util.Assert;

import java.util.Arrays;
import java.util.List;

/**
 * <b>VertexAiImagenUtils</b> is a Utility class for constructing parameter objects for
 * Imagen on Vertex AI requests.
 *
 * @author Sami Marzouki
 */
public abstract class VertexAiImagenUtils {

	public static Value valueOf(boolean n) {
		return Value.newBuilder().setBoolValue(n).build();
	}

	public static Value valueOf(String s) {
		return Value.newBuilder().setStringValue(s).build();
	}

	public static Value valueOf(int n) {
		return Value.newBuilder().setNumberValue(n).build();
	}

	public static Value valueOf(Struct struct) {
		return Value.newBuilder().setStructValue(struct).build();
	}

	public static Value jsonToValue(String json) throws InvalidProtocolBufferException {
		Value.Builder builder = Value.newBuilder();
		JsonFormat.parser().merge(json, builder);
		return builder.build();
	}

	public static List<Integer> calculateSizeFromAspectRatio(String aspectRatio) {
		if (aspectRatio != null) {
			return switch (aspectRatio) {
				case "1:1" -> List.of(1024, 1024);
				case "9:16" -> List.of(900, 1600);
				case "16:9" -> List.of(1600, 900);
				case "3:4" -> List.of(750, 1000);
				case "4:3" -> List.of(1000, 750);
				default -> throw new IllegalStateException("Unexpected value: " + aspectRatio
						+ " aspect ratio must be one of these values : ['1:1', '9:16', '16:9', '3:4', or '4:3']");
			};
		}
		return Arrays.asList(1024, 1024);
	}

	public static class ImageInstanceBuilder {

		public String prompt;

		public static ImageInstanceBuilder of(String prompt) {
			Assert.hasText(prompt, "Prompt must not be empty");
			var builder = new ImageInstanceBuilder();
			builder.prompt = prompt;
			return builder;
		}

		public Struct build() {
			Struct.Builder textBuilder = Struct.newBuilder();
			textBuilder.putFields("prompt", valueOf(this.prompt));
			return textBuilder.build();
		}

	}

	public static class ImageParametersBuilder {

		public Integer sampleCount;

		public Integer seed;

		public String negativePrompt;

		public String aspectRatio;

		public Boolean addWatermark;

		public String storageUri;

		public String personGeneration;

		public String safetySetting;

		public Struct outputOptions;

		public String language;

		public Boolean enhancePrompt;

		public String sampleImageSize;

		public static ImageParametersBuilder of() {
			return new ImageParametersBuilder();
		}

		public ImageParametersBuilder sampleCount(Integer sampleCount) {
			Assert.notNull(sampleCount, "Sample count must not be null");
			this.sampleCount = sampleCount;
			return this;
		}

		public ImageParametersBuilder seed(Integer seed) {
			Assert.notNull(seed, "Seed must not be null");
			this.seed = seed;
			return this;
		}

		public ImageParametersBuilder negativePrompt(String negativePrompt) {
			Assert.notNull(negativePrompt, "Negative prompt must not be null");
			this.negativePrompt = negativePrompt;
			return this;
		}

		public ImageParametersBuilder aspectRatio(String aspectRatio) {
			Assert.notNull(aspectRatio, "Aspect ratio must not be null");
			this.aspectRatio = aspectRatio;
			return this;
		}

		public ImageParametersBuilder addWatermark(Boolean addWatermark) {
			Assert.notNull(addWatermark, "Add watermark must not be null");
			this.addWatermark = addWatermark;
			return this;
		}

		public ImageParametersBuilder storageUri(String storageUri) {
			Assert.notNull(storageUri, "Storage URI must not be null");
			this.storageUri = storageUri;
			return this;
		}

		public ImageParametersBuilder personGeneration(String personGeneration) {
			Assert.notNull(personGeneration, "Person generation must not be null");
			this.personGeneration = personGeneration;
			return this;
		}

		public ImageParametersBuilder safetySetting(String safetySetting) {
			Assert.notNull(safetySetting, "Safety setting must not be null");
			this.safetySetting = safetySetting;
			return this;
		}

		public ImageParametersBuilder outputOptions(Struct outputOptions) {
			Assert.notNull(outputOptions, "Output options must not be null");
			this.outputOptions = outputOptions;
			return this;
		}

		public ImageParametersBuilder language(String language) {
			Assert.notNull(language, "language must not be null");
			this.language = language;
			return this;
		}

		public ImageParametersBuilder enhancePrompt(Boolean enhancePrompt) {
			Assert.notNull(enhancePrompt, "enhancePrompt must not be null");
			this.enhancePrompt = enhancePrompt;
			return this;
		}

		public ImageParametersBuilder sampleImageSize(String sampleImageSize) {
			Assert.notNull(sampleImageSize, "sampleImageSize must not be null");
			this.sampleImageSize = sampleImageSize;
			return this;
		}

		public Struct build() {
			Struct.Builder imageParametersBuilder = Struct.newBuilder();

			if (this.sampleCount != null) {
				imageParametersBuilder.putFields("sampleCount", valueOf(this.sampleCount));
			}
			if (this.seed != null) {
				imageParametersBuilder.putFields("seed", valueOf(this.seed));
			}
			if (this.negativePrompt != null) {
				imageParametersBuilder.putFields("negativePrompt", valueOf(this.negativePrompt));
			}
			if (this.aspectRatio != null) {
				imageParametersBuilder.putFields("aspectRatio", valueOf(this.aspectRatio));
			}
			if (this.addWatermark != null) {
				imageParametersBuilder.putFields("addWatermark", valueOf(this.addWatermark));
			}
			if (this.storageUri != null) {
				imageParametersBuilder.putFields("storageUri", valueOf(this.storageUri));
			}
			if (this.personGeneration != null) {
				imageParametersBuilder.putFields("personGeneration", valueOf(this.personGeneration));
			}
			if (this.safetySetting != null) {
				imageParametersBuilder.putFields("safetySetting", valueOf(this.safetySetting));
			}
			if (this.outputOptions != null) {
				imageParametersBuilder.putFields("outputOptions",
						Value.newBuilder().setStructValue(this.outputOptions).build());
			}
			if (this.language != null) {
				imageParametersBuilder.putFields("language", valueOf(this.language));
			}
			if (this.enhancePrompt != null) {
				imageParametersBuilder.putFields("enhancePrompt", valueOf(this.enhancePrompt));
			}
			if (this.sampleImageSize != null) {
				imageParametersBuilder.putFields("sampleImageSize", valueOf(this.sampleImageSize));
			}
			return imageParametersBuilder.build();
		}

		public static class OutputOptions {

			public String mimeType;

			public Integer compressionQuality;

			public static OutputOptions of() {
				return new OutputOptions();
			}

			public OutputOptions mimeType(String mimeType) {
				Assert.notNull(mimeType, "MIME type must not be null");
				this.mimeType = mimeType;
				return this;
			}

			public OutputOptions compressionQuality(Integer compressionQuality) {
				Assert.notNull(compressionQuality, "Compression quality must not be null");
				this.compressionQuality = compressionQuality;
				return this;
			}

			public Struct build() {
				Struct.Builder outputOptionsBuilder = Struct.newBuilder();

				if (this.mimeType != null) {
					outputOptionsBuilder.putFields("mimeType", valueOf(this.mimeType));
				}
				if (this.compressionQuality != null) {
					outputOptionsBuilder.putFields("compressionQuality", valueOf(this.compressionQuality));
				}
				return outputOptionsBuilder.build();
			}

		}

	}

}
