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

package org.springframework.ai.bedrock.converse.api;

import java.util.Map;

import software.amazon.awssdk.services.bedrockruntime.model.DocumentFormat;
import software.amazon.awssdk.services.bedrockruntime.model.ImageFormat;
import software.amazon.awssdk.services.bedrockruntime.model.VideoFormat;

import org.springframework.ai.model.Media;
import org.springframework.util.MimeType;

/**
 * The BedrockMediaFormat class provides mappings between MIME types and their
 * corresponding Bedrock media formats for documents, images, and videos. It supports
 * conversion of MIME types to specific formats used by the Bedrock runtime.
 *
 * <p>
 * Supported document formats include PDF, CSV, DOC, DOCX, XLS, XLSX, HTML, TXT, and MD.
 * Supported image formats include JPEG, PNG, GIF, and WEBP. Supported video formats
 * include MKV, MOV, MP4, WEBM, FLV, MPEG, MPG, WMV, and 3GP.
 * </p>
 *
 * <p>
 * Usage example:
 * </p>
 * <pre>
 *     String format = BedrockMediaFormat.getFormatAsString(Media.Format.DOC_PDF);
 * </pre>
 *
 * <p>
 * Throws IllegalArgumentException if the MIME type is unsupported.
 * </p>
 *
 * @author Christian Tzolov
 * @since 1.0.0
 */
public abstract class BedrockMediaFormat {

	// @formatter:off
	public static final Map<MimeType, DocumentFormat> DOCUMENT_MAP = Map.of(
		Media.Format.DOC_PDF, DocumentFormat.PDF,
		Media.Format.DOC_CSV, DocumentFormat.CSV,
		Media.Format.DOC_DOC, DocumentFormat.DOC,
		Media.Format.DOC_DOCX, DocumentFormat.DOCX,
		Media.Format.DOC_XLS, DocumentFormat.XLS,
		Media.Format.DOC_XLSX, DocumentFormat.XLSX,
		Media.Format.DOC_HTML, DocumentFormat.HTML,
		Media.Format.DOC_TXT, DocumentFormat.TXT,
		Media.Format.DOC_MD, DocumentFormat.MD);
	// @formatter:on

	// @formatter:off
	public static final Map<MimeType, ImageFormat> IMAGE_MAP = Map.of(
		Media.Format.IMAGE_JPEG, ImageFormat.JPEG,
		Media.Format.IMAGE_PNG, ImageFormat.PNG,
		Media.Format.IMAGE_GIF, ImageFormat.GIF,
		Media.Format.IMAGE_WEBP, ImageFormat.WEBP);
	// @formatter:on

	// @formatter:off
	public static final Map<MimeType, VideoFormat> VIDEO_MAP = Map.of(
		Media.Format.VIDEO_MKV, VideoFormat.MKV,
		Media.Format.VIDEO_MOV, VideoFormat.MOV,
		Media.Format.VIDEO_MP4, VideoFormat.MP4,
		Media.Format.VIDEO_WEBM, VideoFormat.WEBM,
		Media.Format.VIDEO_FLV, VideoFormat.FLV,
		Media.Format.VIDEO_MPEG, VideoFormat.MPEG,
		Media.Format.VIDEO_WMV, VideoFormat.WMV,
		Media.Format.VIDEO_THREE_GP, VideoFormat.THREE_GP);
	// @formatter:on

	public static String getFormatAsString(MimeType mimeType) {
		if (isSupportedDocumentFormat(mimeType)) {
			return DOCUMENT_MAP.get(mimeType).toString();
		}
		else if (isSupportedImageFormat(mimeType)) {
			return IMAGE_MAP.get(mimeType).toString();
		}
		else if (isSupportedVideoFormat(mimeType)) {
			return VIDEO_MAP.get(mimeType).toString();
		}
		throw new IllegalArgumentException("Unsupported media format: " + mimeType);
	}

	public static Boolean isSupportedDocumentFormat(MimeType mimeType) {
		return DOCUMENT_MAP.containsKey(mimeType);
	}

	public static DocumentFormat getDocumentFormat(MimeType mimeType) {
		if (!isSupportedDocumentFormat(mimeType)) {
			throw new IllegalArgumentException("Unsupported document format: " + mimeType);
		}
		return DOCUMENT_MAP.get(mimeType);
	}

	public static Boolean isSupportedImageFormat(MimeType mimeType) {
		return IMAGE_MAP.containsKey(mimeType);
	}

	public static ImageFormat getImageFormat(MimeType mimeType) {
		if (!isSupportedImageFormat(mimeType)) {
			throw new IllegalArgumentException("Unsupported image format: " + mimeType);
		}
		return IMAGE_MAP.get(mimeType);
	}

	public static Boolean isSupportedVideoFormat(MimeType mimeType) {
		return VIDEO_MAP.containsKey(mimeType);
	}

	public static VideoFormat getVideoFormat(MimeType mimeType) {
		if (!isSupportedVideoFormat(mimeType)) {
			throw new IllegalArgumentException("Unsupported video format: " + mimeType);
		}
		return VIDEO_MAP.get(mimeType);
	}

}
