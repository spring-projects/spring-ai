/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.reader.jsoup;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.jsoup.config.JsoupDocumentReaderConfig;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link JsoupDocumentReader}.
 *
 * @author Alexandros Pappas
 */
class JsoupDocumentReaderTests {

	@Test
	void testSimpleRead() {
		JsoupDocumentReader reader = new JsoupDocumentReader("classpath:/test.html");
		List<Document> documents = reader.get();
		assertThat(documents).hasSize(1);
		Document document = documents.get(0);
		assertThat(document.getText()).contains("This is a test HTML document.");
		assertThat(document.getText()).contains("Some paragraph text.");
		assertThat(document.getMetadata()).containsEntry("title", "Test HTML");
		assertThat(document.getMetadata()).containsEntry("description", "A test document for Spring AI");
		assertThat(document.getMetadata()).containsEntry("keywords", "test,html,spring ai");
	}

	@Test
	void testSimpleReadWithAdditionalMetadata() {
		JsoupDocumentReader reader = new JsoupDocumentReader("classpath:/test.html",
				JsoupDocumentReaderConfig.builder().additionalMetadata("key", "value").build());
		List<Document> documents = reader.get();
		assertThat(documents).hasSize(1);
		Document document = documents.get(0);
		assertThat(document.getMetadata()).containsEntry("key", "value");
	}

	@Test
	void testSelector() {
		JsoupDocumentReader reader = new JsoupDocumentReader("classpath:/test.html",
				JsoupDocumentReaderConfig.builder().selector("p").build());
		List<Document> documents = reader.get();
		assertThat(documents).hasSize(1);
		assertThat(documents.get(0).getText()).isEqualTo("Some paragraph text.");
	}

	@Test
	void testAllElements() {
		JsoupDocumentReader reader = new JsoupDocumentReader(
				new DefaultResourceLoader().getResource("classpath:/test.html"),
				JsoupDocumentReaderConfig.builder().allElements(true).build());
		List<Document> documents = reader.get();
		assertThat(documents).hasSize(1);
		Document document = documents.get(0);
		assertThat(document.getText()).contains("This is a test HTML document.");
		assertThat(document.getText()).contains("Some paragraph text.");
	}

	@Test
	void testWithLinkUrls() {
		JsoupDocumentReader reader = new JsoupDocumentReader(
				new DefaultResourceLoader().getResource("classpath:/test.html"),
				JsoupDocumentReaderConfig.builder().includeLinkUrls(true).build());
		List<Document> documents = reader.get();
		assertThat(documents).hasSize(1);
		Document document = documents.get(0);

		assertThat(document.getMetadata()).containsKey("linkUrls");

		List<String> linkUrls = (List<String>) document.getMetadata().get("linkUrls");
		assertThat(linkUrls).contains("https://spring.io/");
	}

	@Test
	void testWithMetadataTags() {
		JsoupDocumentReader reader = new JsoupDocumentReader(
				new DefaultResourceLoader().getResource("classpath:/test.html"),
				JsoupDocumentReaderConfig.builder().metadataTags(List.of("custom1", "custom2")).build());
		List<Document> documents = reader.get();
		assertThat(documents).hasSize(1);
		Document document = documents.get(0);
		assertThat(document.getMetadata()).containsKeys("custom1", "custom2");
		assertThat(document.getMetadata().get("custom1")).isEqualTo("value1");
		assertThat(document.getMetadata().get("custom2")).isEqualTo("value2");
	}

	@Test
	void testWithGroupByElement() {
		JsoupDocumentReader reader = new JsoupDocumentReader(
				new DefaultResourceLoader().getResource("classpath:/test-group-by.html"),
				JsoupDocumentReaderConfig.builder().groupByElement(true).selector("section").build());
		List<Document> documents = reader.get();
		assertThat(documents).hasSize(2);
		assertThat(documents.get(0).getText()).isEqualTo("Section 1 content");
		assertThat(documents.get(1).getText()).isEqualTo("Section 2 content");
	}

	@Test
	@Disabled("This test requires an active internet connection")
	void testWikipediaHeadlines() {
		// Use a URL resource instead of classpath:
		JsoupDocumentReader reader = new JsoupDocumentReader("https://en.wikipedia.org/",
				JsoupDocumentReaderConfig.builder().selector("#mp-itn b a").includeLinkUrls(true).build());

		List<Document> documents = reader.get();
		assertThat(documents).hasSize(1);
		Document document = documents.get(0);

		// Check for *some* content - we don't want to hard-code specific headlines
		// as they will change. This verifies the selector is working.
		assertThat(document.getText()).isNotEmpty();

		// Check if the metadata contains any links
		assertThat(document.getMetadata()).containsKey("linkUrls");
		assertThat(document.getMetadata().get("linkUrls")).isInstanceOf(List.class);
	}

	@Test
	void testParseFromString() {
		String html = "<html><head><title>First parse</title></head>"
				+ "<body><p>Parsed HTML into a doc.</p></body></html>";

		// Decode the base64 string and create a ByteArrayResource
		byte[] htmlBytes = html.getBytes();
		ByteArrayResource byteArrayResource = new ByteArrayResource(htmlBytes);

		JsoupDocumentReader reader = new JsoupDocumentReader(byteArrayResource,
				JsoupDocumentReaderConfig.builder().build());

		List<Document> documents = reader.get();
		assertThat(documents).hasSize(1);
		Document doc = documents.get(0);
		assertThat(doc.getText()).isEqualTo("Parsed HTML into a doc.");
		assertThat(doc.getMetadata()).containsEntry("title", "First parse");
	}

	@Test
	void testParseBodyFragment() {
		String html = "<div><p>Lorem ipsum.</p></div>";

		// Decode the base64 string and create a ByteArrayResource
		byte[] htmlBytes = html.getBytes();
		ByteArrayResource byteArrayResource = new ByteArrayResource(htmlBytes);

		JsoupDocumentReader reader = new JsoupDocumentReader(byteArrayResource,
				JsoupDocumentReaderConfig.builder()
					.selector("div") // Select the div
					.build());

		List<Document> documents = reader.get();
		assertThat(documents).hasSize(1);
		assertThat(documents.get(0).getText()).isEqualTo("Lorem ipsum.");
	}

	@Test
	void testNonExistingHtmlResource() {
		JsoupDocumentReader reader = new JsoupDocumentReader("classpath:/non-existing.html",
				JsoupDocumentReaderConfig.builder().build());
		assertThatThrownBy(reader::get).isInstanceOf(RuntimeException.class);
	}

}
