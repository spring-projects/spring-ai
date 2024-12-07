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

package org.springframework.ai.model;

import java.io.IOException;
import java.net.URL;

import org.springframework.core.io.Resource;
import org.springframework.util.Assert;
import org.springframework.util.MimeType;

/**
 * The Media class represents the data and metadata of a media attachment in a message. It
 * consists of a MIME type and the raw data.
 *
 * This class is used as a parameter in the constructor of the UserMessage class.
 *
 * @author Christian Tzolov
 * @since 0.8.1
 */
public class Media {

	public static final String MEDIA_NO_ID = "nope";

	private final String id;

	private final MimeType mimeType;

	private final Object data;

	private final String name;

	/**
	 * Create a new Media instance.
	 * @param mimeType the media MIME type
	 * @param url the URL for the media data
	 * @deprecated Use {@link Builder} instead.
	 */
	@Deprecated
	public Media(MimeType mimeType, URL url) {
		Assert.notNull(mimeType, "MimeType must not be null");
		this.mimeType = mimeType;
		this.data = url.toString();
		this.id = MEDIA_NO_ID;
		this.name = String.format("media-%s", id);
	}

	/**
	 * Create a new Media instance.
	 * @param mimeType the media MIME type
	 * @param resource the media resource
	 * @deprecated Use {@link Builder} instead.
	 */
	@Deprecated
	public Media(MimeType mimeType, Resource resource) {
		this(mimeType, resource, MEDIA_NO_ID);
	}

	/**
	 * Create a new Media instance.
	 * @param mimeType the media MIME type
	 * @param resource the media resource
	 * @param id the media id
	 * @deprecated Use {@link Builder} instead.
	 */
	@Deprecated
	public Media(MimeType mimeType, Resource resource, String id) {
		Assert.notNull(mimeType, "MimeType must not be null");
		Assert.notNull(id, "Id must not be null");
		this.mimeType = mimeType;
		this.id = id;
		this.name = String.format("media-%s", id, mimeType);
		try {
			this.data = resource.getContentAsByteArray();
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Create a new Media instance.
	 * @param mimeType the media MIME type
	 * @param data the media data
	 * @param id the media id
	 */
	private Media(MimeType mimeType, Object data, String id, String name) {
		Assert.notNull(mimeType, "MimeType must not be null");
		Assert.notNull(id, "Id must not be null");
		Assert.notNull(data, "Data must not be null");
		this.mimeType = mimeType;
		this.id = id;
		this.name = name;
		this.data = data;
	}

	/**
	 * Get the media MIME type
	 * @return the media MIME type
	 */
	public MimeType getMimeType() {
		return this.mimeType;
	}

	/**
	 * Get the media data object
	 * @return a java.net.URL.toString() or a byte[]
	 */
	public Object getData() {
		return this.data;
	}

	/**
	 * Get the media data as a byte array
	 * @return the media data as a byte array
	 */
	public byte[] getDataAsByteArray() {
		if (this.data instanceof byte[]) {
			return (byte[]) this.data;
		}
		else {
			throw new IllegalStateException("Media data is not a byte[]");
		}
	}

	/**
	 * Get the media id
	 * @return the media id
	 */
	public String getId() {
		return this.id;
	}

	public String getName() {
		return this.name;
	}

	/**
	 * Builder class for Media.
	 */
	public static class Builder {

		private String id = MEDIA_NO_ID;

		private MimeType mimeType;

		private Object data;

		private String name;

		public Builder mimeType(MimeType mimeType) {
			Assert.notNull(mimeType, "MimeType must not be null");
			this.mimeType = mimeType;
			return this;
		}

		public Builder data(Resource resource) {
			Assert.notNull(resource, "Data must not be null");
			try {
				this.data = resource.getContentAsByteArray();
			}
			catch (IOException e) {
				throw new IllegalArgumentException(e);
			}
			return this;
		}

		public Builder data(Object data) {
			Assert.notNull(data, "Data must not be null");
			this.data = data;
			return this;
		}

		public Builder data(URL url) {
			Assert.notNull(url, "URL must not be null");
			this.data = url.toString();
			;
			return this;
		}

		public Builder id(String id) {
			Assert.notNull(id, "Id must not be null");
			this.id = id;
			return this;
		}

		public Builder name(String name) {
			Assert.notNull(name, "Name must not be null");
			this.name = name;
			return this;
		}

		public Media build() {
			return new Media(mimeType, data, id, name);
		}

	}

	/**
	 * Common media formats.
	 */
	public static class Format {

		// -----------------
		// Document formats
		// -----------------
		/**
		 * Public constant mime type for {@code application/pdf}.
		 */
		public static final MimeType DOC_PDF = MimeType.valueOf("application/pdf");

		/**
		 * Public constant mime type for {@code text/csv}.
		 */
		public static final MimeType DOC_CSV = MimeType.valueOf("text/csv");

		/**
		 * Public constant mime type for {@code application/msword}.
		 */
		public static final MimeType DOC_DOC = MimeType.valueOf("application/msword");

		/**
		 * Public constant mime type for
		 * {@code application/vnd.openxmlformats-officedocument.wordprocessingml.document}.
		 */
		public static final MimeType DOC_DOCX = MimeType
			.valueOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document");

		/**
		 * Public constant mime type for {@code application/vnd.ms-excel}.
		 */
		public static final MimeType DOC_XLS = MimeType.valueOf("application/vnd.ms-excel");

		/**
		 * Public constant mime type for
		 * {@code application/vnd.openxmlformats-officedocument.spreadsheetml.sheet}.
		 */
		public static final MimeType DOC_XLSX = MimeType
			.valueOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

		/**
		 * Public constant mime type for {@code text/html}.
		 */
		public static final MimeType DOC_HTML = MimeType.valueOf("text/html");

		/**
		 * Public constant mime type for {@code text/plain}.
		 */
		public static final MimeType DOC_TXT = MimeType.valueOf("text/plain");

		/**
		 * Public constant mime type for {@code text/markdown}.
		 */
		public static final MimeType DOC_MD = MimeType.valueOf("text/markdown");

		// -----------------
		// Video Formats
		// -----------------
		/**
		 * Public constant mime type for {@code video/x-matros}.
		 */
		public static final MimeType VIDEO_MKV = MimeType.valueOf("video/x-matros");

		/**
		 * Public constant mime type for {@code video/quicktime}.
		 */
		public static final MimeType VIDEO_MOV = MimeType.valueOf("video/quicktime");

		/**
		 * Public constant mime type for {@code video/mp4}.
		 */
		public static final MimeType VIDEO_MP4 = MimeType.valueOf("video/mp4");

		/**
		 * Public constant mime type for {@code video/webm}.
		 */
		public static final MimeType VIDEO_WEBM = MimeType.valueOf("video/webm");

		/**
		 * Public constant mime type for {@code video/x-flv}.
		 */
		public static final MimeType VIDEO_FLV = MimeType.valueOf("video/x-flv");

		/**
		 * Public constant mime type for {@code video/mpeg}.
		 */
		public static final MimeType VIDEO_MPEG = MimeType.valueOf("video/mpeg");

		/**
		 * Public constant mime type for {@code video/mpeg}.
		 */
		public static final MimeType VIDEO_MPG = MimeType.valueOf("video/mpeg");

		/**
		 * Public constant mime type for {@code video/x-ms-wmv}.
		 */
		public static final MimeType VIDEO_WMV = MimeType.valueOf("video/x-ms-wmv");

		/**
		 * Public constant mime type for {@code video/3gpp}.
		 */
		public static final MimeType VIDEO_THREE_GP = MimeType.valueOf("video/3gpp");

		// -----------------
		// Image Formats
		// -----------------
		/**
		 * Public constant mime type for {@code image/png}.
		 */
		public static final MimeType IMAGE_PNG = MimeType.valueOf("image/png");

		/**
		 * Public constant mime type for {@code image/jpeg}.
		 */
		public static final MimeType IMAGE_JPEG = MimeType.valueOf("image/jpeg");

		/**
		 * Public constant mime type for {@code image/gif}.
		 */
		public static final MimeType IMAGE_GIF = MimeType.valueOf("image/gif");

		/**
		 * Public constant mime type for {@code image/webp}.
		 */
		public static final MimeType IMAGE_WEBP = MimeType.valueOf("image/webp");

	}

}
