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

import org.junit.jupiter.api.Test;
import org.springframework.ai.model.Media;
import org.springframework.util.MimeType;
import software.amazon.awssdk.services.bedrockruntime.model.DocumentFormat;
import software.amazon.awssdk.services.bedrockruntime.model.ImageFormat;
import software.amazon.awssdk.services.bedrockruntime.model.VideoFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BedrockMediaFormatTest {

	@Test
	void testSupportedDocumentFormats() {
		// Test all supported document formats
		assertThat(BedrockMediaFormat.DOCUMENT_MAP.get(Media.Format.DOC_PDF)).isEqualTo(DocumentFormat.PDF);
		assertThat(BedrockMediaFormat.DOCUMENT_MAP.get(Media.Format.DOC_CSV)).isEqualTo(DocumentFormat.CSV);
		assertThat(BedrockMediaFormat.DOCUMENT_MAP.get(Media.Format.DOC_DOC)).isEqualTo(DocumentFormat.DOC);
		assertThat(BedrockMediaFormat.DOCUMENT_MAP.get(Media.Format.DOC_DOCX)).isEqualTo(DocumentFormat.DOCX);
		assertThat(BedrockMediaFormat.DOCUMENT_MAP.get(Media.Format.DOC_XLS)).isEqualTo(DocumentFormat.XLS);
		assertThat(BedrockMediaFormat.DOCUMENT_MAP.get(Media.Format.DOC_XLSX)).isEqualTo(DocumentFormat.XLSX);
		assertThat(BedrockMediaFormat.DOCUMENT_MAP.get(Media.Format.DOC_HTML)).isEqualTo(DocumentFormat.HTML);
		assertThat(BedrockMediaFormat.DOCUMENT_MAP.get(Media.Format.DOC_TXT)).isEqualTo(DocumentFormat.TXT);
		assertThat(BedrockMediaFormat.DOCUMENT_MAP.get(Media.Format.DOC_MD)).isEqualTo(DocumentFormat.MD);
	}

	@Test
	void testSupportedImageFormats() {
		// Test all supported image formats
		assertThat(BedrockMediaFormat.IMAGE_MAP.get(Media.Format.IMAGE_JPEG)).isEqualTo(ImageFormat.JPEG);
		assertThat(BedrockMediaFormat.IMAGE_MAP.get(Media.Format.IMAGE_PNG)).isEqualTo(ImageFormat.PNG);
		assertThat(BedrockMediaFormat.IMAGE_MAP.get(Media.Format.IMAGE_GIF)).isEqualTo(ImageFormat.GIF);
		assertThat(BedrockMediaFormat.IMAGE_MAP.get(Media.Format.IMAGE_WEBP)).isEqualTo(ImageFormat.WEBP);
	}

	@Test
	void testSupportedVideoFormats() {
		// Test all supported video formats
		assertThat(BedrockMediaFormat.VIDEO_MAP.get(Media.Format.VIDEO_MKV)).isEqualTo(VideoFormat.MKV);
		assertThat(BedrockMediaFormat.VIDEO_MAP.get(Media.Format.VIDEO_MOV)).isEqualTo(VideoFormat.MOV);
		assertThat(BedrockMediaFormat.VIDEO_MAP.get(Media.Format.VIDEO_MP4)).isEqualTo(VideoFormat.MP4);
		assertThat(BedrockMediaFormat.VIDEO_MAP.get(Media.Format.VIDEO_WEBM)).isEqualTo(VideoFormat.WEBM);
		assertThat(BedrockMediaFormat.VIDEO_MAP.get(Media.Format.VIDEO_FLV)).isEqualTo(VideoFormat.FLV);
		assertThat(BedrockMediaFormat.VIDEO_MAP.get(Media.Format.VIDEO_MPEG)).isEqualTo(VideoFormat.MPEG);
		assertThat(BedrockMediaFormat.VIDEO_MAP.get(Media.Format.VIDEO_MPG)).isEqualTo(VideoFormat.MPEG);
		assertThat(BedrockMediaFormat.VIDEO_MAP.get(Media.Format.VIDEO_WMV)).isEqualTo(VideoFormat.WMV);
		assertThat(BedrockMediaFormat.VIDEO_MAP.get(Media.Format.VIDEO_THREE_GP)).isEqualTo(VideoFormat.THREE_GP);
	}

	@Test
	void testIsSupportedDocumentFormat() {
		// Test supported document formats
		assertThat(BedrockMediaFormat.isSupportedDocumentFormat(Media.Format.DOC_PDF)).isTrue();
		assertThat(BedrockMediaFormat.isSupportedDocumentFormat(Media.Format.DOC_CSV)).isTrue();

		// Test unsupported document format
		assertThat(BedrockMediaFormat.isSupportedDocumentFormat(MimeType.valueOf("application/unknown"))).isFalse();
	}

	@Test
	void testIsSupportedImageFormat() {
		// Test supported image formats
		assertThat(BedrockMediaFormat.isSupportedImageFormat(Media.Format.IMAGE_JPEG)).isTrue();
		assertThat(BedrockMediaFormat.isSupportedImageFormat(Media.Format.IMAGE_PNG)).isTrue();

		// Test unsupported image format
		assertThat(BedrockMediaFormat.isSupportedImageFormat(MimeType.valueOf("image/tiff"))).isFalse();
	}

	@Test
	void testIsSupportedVideoFormat() {
		// Test supported video formats
		assertThat(BedrockMediaFormat.isSupportedVideoFormat(Media.Format.VIDEO_MP4)).isTrue();
		assertThat(BedrockMediaFormat.isSupportedVideoFormat(Media.Format.VIDEO_MOV)).isTrue();

		// Test unsupported video format
		assertThat(BedrockMediaFormat.isSupportedVideoFormat(MimeType.valueOf("video/avi"))).isFalse();
	}

	@Test
	void testGetFormatAsString() {
		// Test document format conversion
		assertThat(BedrockMediaFormat.getFormatAsString(Media.Format.DOC_PDF)).isEqualTo(DocumentFormat.PDF.toString());

		// Test image format conversion
		assertThat(BedrockMediaFormat.getFormatAsString(Media.Format.IMAGE_JPEG))
			.isEqualTo(ImageFormat.JPEG.toString());

		// Test video format conversion
		assertThat(BedrockMediaFormat.getFormatAsString(Media.Format.VIDEO_MP4)).isEqualTo(VideoFormat.MP4.toString());
	}

	@Test
	void testGetFormatAsStringWithUnsupportedFormat() {
		// Test that an IllegalArgumentException is thrown for unsupported format
		MimeType unsupportedFormat = MimeType.valueOf("application/unknown");

		assertThatThrownBy(() -> BedrockMediaFormat.getFormatAsString(unsupportedFormat))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Unsupported media format: " + unsupportedFormat);
	}

	@Test
	void testGetImageFormat() {
		// Test getting image formats
		assertThat(BedrockMediaFormat.getImageFormat(Media.Format.IMAGE_JPEG)).isEqualTo(ImageFormat.JPEG);
		assertThat(BedrockMediaFormat.getImageFormat(Media.Format.IMAGE_PNG)).isEqualTo(ImageFormat.PNG);
	}

}
