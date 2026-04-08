/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.reader.tika;

import org.junit.jupiter.api.Test;

import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TikaRemoteDocumentReader}.
 *
 * @author Sahil Bhardwaj
 */
class TikaRemoteDocumentReaderTests {

	@Test
	void defaultTikaServerUrl() {
		assertThat(TikaRemoteDocumentReader.DEFAULT_TIKA_SERVER_URL).isEqualTo("http://localhost:9998");
	}

	@Test
	void metadataSourceConstant() {
		assertThat(TikaRemoteDocumentReader.METADATA_SOURCE).isEqualTo("source");
	}

	@Test
	void constructsWithResourceOnly() {
		Resource resource = new ClassPathResource("word-sample.docx");
		var reader = new TikaRemoteDocumentReader(resource);
		assertThat(reader).isNotNull();
	}

	@Test
	void constructsWithTikaUrlAndResource() {
		Resource resource = new ClassPathResource("word-sample.docx");
		var reader = new TikaRemoteDocumentReader("http://localhost:9998", resource);
		assertThat(reader).isNotNull();
	}

	@Test
	void constructsWithCustomRestClient() {
		Resource resource = new ClassPathResource("word-sample.docx");
		RestClient customClient = RestClient.builder().build();
		var reader = new TikaRemoteDocumentReader("http://localhost:9998", resource, ExtractedTextFormatter.defaults(),
				customClient);
		assertThat(reader).isNotNull();
	}

}
