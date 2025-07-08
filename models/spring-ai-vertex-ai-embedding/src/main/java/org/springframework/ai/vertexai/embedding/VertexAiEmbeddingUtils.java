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

package org.springframework.ai.vertexai.embedding;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.google.protobuf.util.JsonFormat;

import org.springframework.util.Assert;
import org.springframework.util.MimeType;
import org.springframework.util.StringUtils;

/**
 * Utility class for constructing parameter objects for Vertex AI embedding requests.
 *
 * @author Christian Tzolov
 * @author Ilayaperumal Gopinathan
 * @since 1.0.0
 */
public abstract class VertexAiEmbeddingUtils {

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

	// Convert a Json string to a protobuf.Value
	public static Value jsonToValue(String json) throws InvalidProtocolBufferException {
		Value.Builder builder = Value.newBuilder();
		JsonFormat.parser().merge(json, builder);
		return builder.build();
	}

	public static float[] toVector(Value value) {
		float[] floats = new float[value.getListValue().getValuesList().size()];
		int index = 0;
		for (Value v : value.getListValue().getValuesList()) {
			double d = v.getNumberValue();
			floats[index++] = Double.valueOf(d).floatValue();
		}
		return floats;
	}

	//////////////////////////////////////////////////////
	// Text Only
	//////////////////////////////////////////////////////
	public static class TextParametersBuilder {

		public Integer outputDimensionality;

		public Boolean autoTruncate;

		public static TextParametersBuilder of() {
			return new TextParametersBuilder();
		}

		public TextParametersBuilder outputDimensionality(Integer outputDimensionality) {
			Assert.notNull(outputDimensionality, "Output dimensionality must not be null");
			this.outputDimensionality = outputDimensionality;
			return this;
		}

		public TextParametersBuilder autoTruncate(Boolean autoTruncate) {
			Assert.notNull(autoTruncate, "Auto truncate must not be null");
			this.autoTruncate = autoTruncate;
			return this;
		}

		public Struct build() {
			Struct.Builder textParametersBuilder = Struct.newBuilder();

			if (this.outputDimensionality != null) {
				textParametersBuilder.putFields("outputDimensionality", valueOf(this.outputDimensionality));
			}
			if (this.autoTruncate != null) {
				textParametersBuilder.putFields("autoTruncate", valueOf(this.autoTruncate));
			}
			return textParametersBuilder.build();
		}

	}

	public static class TextInstanceBuilder {

		public String content;

		public String taskType;

		public String title;

		public static TextInstanceBuilder of(String content) {
			Assert.hasText(content, "Content must not be empty");
			var builder = new TextInstanceBuilder();
			builder.content = content;
			return builder;
		}

		public TextInstanceBuilder taskType(String taskType) {
			Assert.hasText(taskType, "Task type must not be empty");
			this.taskType = taskType;
			return this;
		}

		public TextInstanceBuilder title(String title) {
			Assert.hasText(title, "Title must not be empty");
			this.title = title;
			return this;
		}

		public Struct build() {
			Struct.Builder textBuilder = Struct.newBuilder();
			textBuilder.putFields("content", valueOf(this.content));
			if (StringUtils.hasText(this.taskType)) {
				textBuilder.putFields("task_type", valueOf(this.taskType));
			}
			if (StringUtils.hasText(this.title)) {
				textBuilder.putFields("title", valueOf(this.title));
			}
			return textBuilder.build();
		}

	}

	//////////////////////////////////////////////////////
	// Multimodality
	//////////////////////////////////////////////////////
	public static class MultimodalInstanceBuilder {

		/**
		 * The text to generate embeddings for.
		 */
		private String text;

		/**
		 * The dimension of the embedding, included in the response. Only applies to text
		 * and image input. Accepted values: 128, 256, 512, or 1408.
		 */
		private Integer dimension;

		/**
		 * The image to generate embeddings for.
		 */
		private Struct image;

		/**
		 * The video segment to generate embeddings for.
		 */
		private Struct video;

		public static MultimodalInstanceBuilder of() {
			return new MultimodalInstanceBuilder();
		}

		public MultimodalInstanceBuilder text(String text) {
			Assert.hasText(text, "Text must not be empty");
			this.text = text;
			return this;
		}

		public MultimodalInstanceBuilder dimension(Integer dimension) {
			Assert.isTrue(dimension == 128 || dimension == 256 || dimension == 512 || dimension == 1408,
					"Invalid dimension value: " + dimension + ". Accepted values: 128, 256, 512, or 1408.");
			this.dimension = dimension;
			return this;
		}

		public MultimodalInstanceBuilder image(Struct image) {
			Assert.notNull(image, "Image must not be null");
			this.image = image;
			return this;
		}

		public MultimodalInstanceBuilder video(Struct video) {
			Assert.notNull(video, "Video must not be null");
			this.video = video;
			return this;
		}

		public Struct build() {
			Struct.Builder builder = Struct.newBuilder();

			if (this.text != null) {
				builder.putFields("text", valueOf(this.text));
			}
			if (this.dimension != null) {
				Struct.Builder dimensionBuilder = Struct.newBuilder();
				dimensionBuilder.putFields("dimension", valueOf(this.dimension));
				builder.putFields("parameters", Value.newBuilder().setStructValue(dimensionBuilder.build()).build());
			}
			if (this.image != null) {
				builder.putFields("image", Value.newBuilder().setStructValue(this.image).build());
			}
			if (this.video != null) {
				builder.putFields("video", Value.newBuilder().setStructValue(this.video).build());
			}

			Assert.isTrue(builder.getFieldsCount() > 0, "At least one of the text, image or video must be set");

			return builder.build();
		}

	}

	public static class ImageBuilder {

		/**
		 * Image bytes to be encoded in a base64 string.
		 */
		public byte[] imageBytes;

		/**
		 * The Cloud Storage location of the image to perform the embedding. One of
		 * bytesBase64Encoded or gcsUri.
		 */
		public String gcsUri;

		/**
		 * The MIME type of the content of the image. Supported values: image/jpeg and
		 * image/png.
		 */
		public MimeType mimeType;

		public static ImageBuilder of(MimeType mimeType) {
			Assert.notNull(mimeType, "MimeType must not be null");
			var builder = new ImageBuilder();
			builder.mimeType = mimeType;
			return builder;
		}

		public ImageBuilder imageData(Object imageData) {
			Assert.notNull(imageData, "Image data must not be null");
			if (imageData instanceof byte[] bytes) {
				return imageBytes(bytes);
			}
			else if (imageData instanceof String uri) {
				return gcsUri(uri);
			}
			else {
				throw new IllegalArgumentException("Unsupported image data type: " + imageData.getClass());
			}
		}

		public ImageBuilder imageBytes(byte[] imageBytes) {
			Assert.notNull(imageBytes, "Image bytes must not be null");
			this.imageBytes = imageBytes;
			return this;
		}

		public ImageBuilder gcsUri(String gcsUri) {
			Assert.hasText(gcsUri, "GCS URI must not be empty");
			this.gcsUri = gcsUri;
			return this;
		}

		public Struct build() {

			Struct.Builder imageBuilder = Struct.newBuilder();

			if (this.imageBytes != null) {
				byte[] imageData = Base64.getEncoder().encode(this.imageBytes);
				String encodedImage = new String(imageData, StandardCharsets.UTF_8);
				imageBuilder.putFields("bytesBase64Encoded", valueOf(encodedImage));
			}
			else if (this.gcsUri != null) {
				imageBuilder.putFields("gcsUri", valueOf(this.gcsUri));
			}
			if (this.mimeType != null) {
				imageBuilder.putFields("mimeType", valueOf(this.mimeType.toString()));
			}

			Assert.isTrue(imageBuilder.getFieldsCount() > 0, "At least one of the imageBytes or gcsUri must be set");

			return imageBuilder.build();
		}

	}

	public static class VideoBuilder {

		/**
		 * Video bytes to be encoded in base64 string. One of videoBytes or gcsUri.
		 */
		public byte[] videoBytes;

		/**
		 * The Cloud Storage location of the video on which to perform the embedding. One
		 * of videoBytes or gcsUri.
		 */
		public String gcsUri;

		/**
		 *
		 */
		public MimeType mimeType;

		/**
		 * The start offset of the video segment in seconds. If not specified, it's
		 * calculated with max(0, endOffsetSec - 120).
		 */
		public Integer startOffsetSec;

		/**
		 * The end offset of the video segment in seconds. If not specified, it's
		 * calculated with min(video length, startOffSec + 120). If both startOffSec and
		 * endOffSec are specified, endOffsetSec is adjusted to min(startOffsetSec+120,
		 * endOffsetSec).
		 */
		public Integer endOffsetSec;

		/**
		 * The interval of the video the embedding will be generated. The minimum value
		 * for interval_sec is 4. If the interval is less than 4, an InvalidArgumentError
		 * is returned. There are no limitations on the maximum value of the interval.
		 * However, if the interval is larger than min(video length, 120s), it impacts the
		 * quality of the generated embeddings. Default value: 16.
		 */
		public Integer intervalSec;

		public static VideoBuilder of(MimeType mimeType) {
			Assert.notNull(mimeType, "MimeType must not be null");
			var builder = new VideoBuilder();
			builder.mimeType = mimeType;
			return builder;
		}

		public VideoBuilder videoData(Object imageData) {
			Assert.notNull(imageData, "Video data must not be null");
			if (imageData instanceof byte[] imageBytes) {
				return videoBytes(imageBytes);
			}
			else if (imageData instanceof String uri) {
				return gcsUri(uri);
			}
			else {
				throw new IllegalArgumentException("Unsupported image data type: " + imageData.getClass());
			}
		}

		public VideoBuilder videoBytes(byte[] imageBytes) {
			Assert.notNull(imageBytes, "Video bytes must not be null");
			this.videoBytes = imageBytes;
			return this;
		}

		public VideoBuilder gcsUri(String gcsUri) {
			Assert.hasText(gcsUri, "GCS URI must not be empty");
			this.gcsUri = gcsUri;
			return this;
		}

		public VideoBuilder startOffsetSec(Integer startOffsetSec) {
			if (startOffsetSec != null) {
				this.startOffsetSec = startOffsetSec;
			}
			return this;
		}

		public VideoBuilder endOffsetSec(Integer endOffsetSec) {
			if (endOffsetSec != null) {
				this.endOffsetSec = endOffsetSec;
			}
			return this;

		}

		public VideoBuilder intervalSec(Integer intervalSec) {
			if (intervalSec != null) {
				this.intervalSec = intervalSec;
			}
			return this;
		}

		public Struct build() {

			Struct.Builder videoBuilder = Struct.newBuilder();

			if (this.videoBytes != null) {
				byte[] imageData = Base64.getEncoder().encode(this.videoBytes);
				String encodedImage = new String(imageData, StandardCharsets.UTF_8);
				videoBuilder.putFields("bytesBase64Encoded", valueOf(encodedImage));
			}
			else if (this.gcsUri != null) {
				videoBuilder.putFields("gcsUri", valueOf(this.gcsUri));
			}
			if (this.mimeType != null) {
				videoBuilder.putFields("mimeType", valueOf(this.mimeType.toString()));
			}

			Struct.Builder videoConfigBuilder = Struct.newBuilder();

			if (this.startOffsetSec != null) {
				videoConfigBuilder.putFields("startOffsetSec", valueOf(this.startOffsetSec));
			}
			if (this.endOffsetSec != null) {
				videoConfigBuilder.putFields("endOffsetSec", valueOf(this.endOffsetSec));
			}
			if (this.intervalSec != null) {
				videoConfigBuilder.putFields("intervalSec", valueOf(this.intervalSec));
			}
			if (videoConfigBuilder.getFieldsCount() > 0) {
				videoBuilder.putFields("videoSegmentConfig",
						Value.newBuilder().setStructValue(videoConfigBuilder.build()).build());
			}

			Assert.isTrue(videoBuilder.getFieldsCount() > 0, "At least one of the videoBytes or gcsUri must be set");

			return videoBuilder.build();
		}

	}

}
