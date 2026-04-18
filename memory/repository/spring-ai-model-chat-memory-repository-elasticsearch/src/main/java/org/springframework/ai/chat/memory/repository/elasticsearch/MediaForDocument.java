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

package org.springframework.ai.chat.memory.repository.elasticsearch;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.content.Media;
import org.springframework.util.Assert;
import org.springframework.util.MimeType;

public record MediaForDocument(@Nullable String id, String name, String mimeType, @Nullable String contentString,
		byte @Nullable [] contentBytes) {

	public static MediaForDocument fromMedia(Media media) {
		if (media.getData() instanceof byte[] mediaBytes) {
			return new MediaForDocument(media.getId(), media.getName(), media.getMimeType().toString(), null,
					mediaBytes);
		}
		return new MediaForDocument(media.getId(), media.getName(), media.getMimeType().toString(),
				(String) media.getData(), null);
	}

	public static Media toMedia(MediaForDocument mediaForDocument) {
		Object data = mediaForDocument.contentBytes == null ? mediaForDocument.contentString
				: mediaForDocument.contentBytes;
		// data will never be null, because it will be either string or byte, but this
		// fixes compilation
		Assert.notNull(data, "Data must not be null");
		Media.Builder builder = Media.builder()
			.name(mediaForDocument.name())
			.data(data)
			.mimeType(MimeType.valueOf(mediaForDocument.mimeType()));
		if (mediaForDocument.id() != null) {
			builder.id(mediaForDocument.id());
		}
		return builder.build();

	}
}
