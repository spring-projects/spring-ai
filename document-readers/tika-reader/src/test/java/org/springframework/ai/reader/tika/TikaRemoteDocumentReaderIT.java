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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link TikaRemoteDocumentReader} using Apache Tika Server in
 * Docker.
 *
 * @author Sahil Bhardwaj
 */
@Testcontainers
class TikaRemoteDocumentReaderIT {

	private static final int TIKA_PORT = 9998;

	@Container
	static GenericContainer<?> tika = new GenericContainer<>(DockerImageName.parse("apache/tika:2.9.2.1"))
		.withExposedPorts(TIKA_PORT);

	@Test
	void extractTextFromDocx() {
		String tikaUrl = "http://%s:%d".formatted(tika.getHost(), tika.getMappedPort(TIKA_PORT));
		Resource resource = new ClassPathResource("word-sample.docx");

		var docs = new TikaRemoteDocumentReader(tikaUrl, resource).get();

		assertThat(docs).hasSize(1);
		assertThat(docs.get(0).getMetadata()).containsKeys(TikaRemoteDocumentReader.METADATA_SOURCE);
		assertThat(docs.get(0).getMetadata().get(TikaRemoteDocumentReader.METADATA_SOURCE)).isEqualTo("word-sample.docx");
		assertThat(docs.get(0).getText()).contains("Two kinds of links are possible");
		assertThat(docs.get(0).getText()).contains("those that refer to an external website");
	}

	@Test
	void extractTextFromDocxWithFormatter() {
		String tikaUrl = "http://%s:%d".formatted(tika.getHost(), tika.getMappedPort(TIKA_PORT));
		Resource resource = new ClassPathResource("word-sample.docx");
		ExtractedTextFormatter formatter = ExtractedTextFormatter.builder().withNumberOfTopTextLinesToDelete(5).build();

		var docs = new TikaRemoteDocumentReader(tikaUrl, resource, formatter).get();

		assertThat(docs).hasSize(1);
		assertThat(docs.get(0).getText()).doesNotContain("This document demonstrates the ability of the calibre DOCX Input plugin");
	}

	@Test
	void extractTextFromPdf() {
		String tikaUrl = "http://%s:%d".formatted(tika.getHost(), tika.getMappedPort(TIKA_PORT));
		Resource resource = new ClassPathResource("sample2.pdf");

		var docs = new TikaRemoteDocumentReader(tikaUrl, resource).get();

		assertThat(docs).hasSize(1);
		assertThat(docs.get(0).getMetadata().get(TikaRemoteDocumentReader.METADATA_SOURCE)).isEqualTo("sample2.pdf");
		assertThat(docs.get(0).getText()).contains("doc/pdftex/manual.pdf");
	}

	@Test
	void extractTextFromPptx() {
		String tikaUrl = "http://%s:%d".formatted(tika.getHost(), tika.getMappedPort(TIKA_PORT));
		Resource resource = new ClassPathResource("sample.pptx");

		var docs = new TikaRemoteDocumentReader(tikaUrl, resource).get();

		assertThat(docs).hasSize(1);
		assertThat(docs.get(0).getText()).contains("Lorem ipsum dolor sit amet");
	}

}
