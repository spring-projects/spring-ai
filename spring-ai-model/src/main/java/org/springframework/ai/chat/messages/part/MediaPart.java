/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.chat.messages.part;

import java.net.URI;
import java.net.URL;
import java.util.Base64;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.util.StdConverter;

import org.springframework.ai.content.Media;
import org.springframework.util.Assert;
import org.springframework.util.MimeType;

/**
 * A media element (image, audio, video, document) produced by the model or supplied by
 * the caller.
 * <p>
 * {@link Media#getData()} is an untyped {@code Object} ({@code byte[]}, {@code String} or
 * {@link URL}), so the media is serialized through a small DTO that records which kind of
 * data it held and restores the same kind on read.
 *
 * @param media the media
 * @param payload provider data to replay with this part, or {@code null}
 * @param attributes free-form attributes, copied and unmodifiable
 * @author Christian Tzolov
 * @since 2.1.0
 */
public record MediaPart(
		@JsonSerialize(converter = MediaPart.ToDto.class) @JsonDeserialize(
				converter = MediaPart.FromDto.class) Media media,
		@Nullable OpaquePayload payload, Map<String, String> attributes) implements MessagePart {

	public MediaPart {
		Assert.notNull(media, "media must not be null");
		Assert.notNull(attributes, "attributes must not be null");
		attributes = Map.copyOf(attributes);
	}

	@Override
	public MediaPart withAttributes(Map<String, String> attributes) {
		return new MediaPart(this.media, this.payload, attributes);
	}

	/**
	 * Creates a media part without payload or attributes.
	 * @param media the media
	 * @return the part
	 */
	public static MediaPart of(Media media) {
		return new MediaPart(media, null, Map.of());
	}

	/**
	 * Serialized form of a {@link Media}.
	 *
	 * @param id the media id, or {@code null}
	 * @param mimeType the MIME type as a string
	 * @param name the media name
	 * @param data the data: base64 text for {@code bytes}, the string itself for
	 * {@code string}, the URL text for {@code url}
	 * @param dataKind one of {@code bytes}, {@code string}, {@code url}
	 */
	public record MediaDto(@Nullable String id, String mimeType, String name, String data, String dataKind) {

		static final String BYTES = "bytes";

		static final String STRING = "string";

		static final String URL_KIND = "url";

		static MediaDto from(Media media) {
			Object data = media.getData();
			String mimeType = media.getMimeType().toString();
			if (data instanceof byte[] bytes) {
				return new MediaDto(media.getId(), mimeType, media.getName(), Base64.getEncoder().encodeToString(bytes),
						BYTES);
			}
			if (data instanceof URL url) {
				return new MediaDto(media.getId(), mimeType, media.getName(), url.toString(), URL_KIND);
			}
			return new MediaDto(media.getId(), mimeType, media.getName(), data.toString(), STRING);
		}

		Media toMedia() {
			Media.Builder builder = Media.builder().mimeType(MimeType.valueOf(this.mimeType)).name(this.name);
			if (this.id != null) {
				builder.id(this.id);
			}
			if (BYTES.equals(this.dataKind)) {
				return builder.data(Base64.getDecoder().decode(this.data)).build();
			}
			if (URL_KIND.equals(this.dataKind)) {
				return builder.data(URI.create(this.data)).build();
			}
			return builder.data(this.data).build();
		}

	}

	/**
	 * Jackson converter from {@link Media} to {@link MediaDto}.
	 */
	public static final class ToDto extends StdConverter<Media, MediaDto> {

		@Override
		public MediaDto convert(Media media) {
			return MediaDto.from(media);
		}

	}

	/**
	 * Jackson converter from {@link MediaDto} to {@link Media}.
	 */
	public static final class FromDto extends StdConverter<MediaDto, Media> {

		@Override
		public Media convert(MediaDto dto) {
			return dto.toMedia();
		}

	}

}
