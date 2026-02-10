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

package org.springframework.ai.reader.markdown;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

/**
 * Unit tests for {@link MarkdownDocumentReader}.
 *
 * @author Piotr Olaszewski
 * @author shown.Ji
 * @author Eric Bottard
 * @author Songhee An
 */
class MarkdownDocumentReaderTest {

	@Test
	void testDirPathSingle() {
		MarkdownDocumentReader reader = new MarkdownDocumentReader("classpath:/dir-test-1/*.md");

		List<Document> documents = reader.get();

		assertThat(documents).hasSize(2)
			.extracting(Document::getMetadata, Document::getText)
			.containsOnly(tuple(Map.of(),
					"Lorem ipsum dolor sit amet, consectetur adipiscing elit. Curabitur diam eros, laoreet sit amet cursus vitae, varius sed nisi. Cras sit amet quam quis velit commodo porta consectetur id nisi. Phasellus tincidunt pulvinar augue."),
					tuple(Map.of("category", "blockquote"),
							"Proin vel laoreet leo, sed luctus augue. Sed et ligula commodo, commodo lacus at, consequat turpis. Maecenas eget sapien odio. Maecenas urna lectus, pellentesque in accumsan aliquam, congue eu libero. Ut rhoncus nec justo a porttitor. Pellentesque auctor pharetra eros, viverra sodales lorem aliquet id. Curabitur semper nisi vel sem interdum suscipit."));
	}

	@Test
	void testDirPathMultiple() {
		MarkdownDocumentReader reader = new MarkdownDocumentReader("classpath:/dir-test-2/*.md");
		List<Document> documents = reader.get();

		assertThat(documents).hasSize(6)
			.extracting(Document::getMetadata, Document::getText)
			.containsOnly(tuple(Map.of("category", "header_1", "title", "This is a fancy header name"),
					"Lorem ipsum dolor sit amet, consectetur adipiscing elit. Donec tincidunt velit non bibendum gravida. Cras accumsan tincidunt ornare. Donec hendrerit consequat tellus blandit accumsan. Aenean aliquam metus at arcu elementum dignissim."),
					tuple(Map.of("category", "header_3", "title", "Header 3"),
							"Aenean eu leo eu nibh tristique posuere quis quis massa."),
					tuple(Map.of("category", "header_1", "title", "Header 1a"),
							"Lorem ipsum dolor sit amet, consectetur adipiscing elit. Curabitur diam eros, laoreet sit amet cursus vitae, varius sed nisi. Cras sit amet quam quis velit commodo porta consectetur id nisi. Phasellus tincidunt pulvinar augue."),
					tuple(Map.of("category", "header_1", "title", "Header 1b"),
							"Vestibulum ante ipsum primis in faucibus orci luctus et ultrices posuere cubilia curae; Etiam lobortis risus libero, sed sollicitudin risus cursus in. Morbi enim metus, ornare vel lacinia eget, venenatis vel nibh."),
					tuple(Map.of("category", "header_2", "title", "Header 2b"),
							"Proin vel laoreet leo, sed luctus augue. Sed et ligula commodo, commodo lacus at, consequat turpis. Maecenas eget sapien odio. Maecenas urna lectus, pellentesque in accumsan aliquam, congue eu libero."),
					tuple(Map.of("category", "header_2", "title", "Header 2c"),
							"Ut rhoncus nec justo a porttitor. Pellentesque auctor pharetra eros, viverra sodales lorem aliquet id. Curabitur semper nisi vel sem interdum suscipit."));
	}

	@Test
	void testOnlyHeadersWithParagraphs() {
		MarkdownDocumentReader reader = new MarkdownDocumentReader("classpath:/only-headers.md");

		List<Document> documents = reader.get();

		assertThat(documents).hasSize(4)
			.extracting(Document::getMetadata, Document::getText)
			.containsOnly(tuple(Map.of("category", "header_1", "title", "Header 1a"),
					"Lorem ipsum dolor sit amet, consectetur adipiscing elit. Curabitur diam eros, laoreet sit amet cursus vitae, varius sed nisi. Cras sit amet quam quis velit commodo porta consectetur id nisi. Phasellus tincidunt pulvinar augue."),
					tuple(Map.of("category", "header_1", "title", "Header 1b"),
							"Vestibulum ante ipsum primis in faucibus orci luctus et ultrices posuere cubilia curae; Etiam lobortis risus libero, sed sollicitudin risus cursus in. Morbi enim metus, ornare vel lacinia eget, venenatis vel nibh."),
					tuple(Map.of("category", "header_2", "title", "Header 2b"),
							"Proin vel laoreet leo, sed luctus augue. Sed et ligula commodo, commodo lacus at, consequat turpis. Maecenas eget sapien odio. Maecenas urna lectus, pellentesque in accumsan aliquam, congue eu libero."),
					tuple(Map.of("category", "header_2", "title", "Header 2c"),
							"Ut rhoncus nec justo a porttitor. Pellentesque auctor pharetra eros, viverra sodales lorem aliquet id. Curabitur semper nisi vel sem interdum suscipit."));
	}

	@Test
	void testWithFormatting() {
		MarkdownDocumentReader reader = new MarkdownDocumentReader("classpath:/with-formatting.md");

		List<Document> documents = reader.get();

		assertThat(documents).hasSize(2)
			.extracting(Document::getMetadata, Document::getText)
			.containsOnly(tuple(Map.of("category", "header_1", "title", "This is a fancy header name"),
					"Lorem ipsum dolor sit amet, consectetur adipiscing elit. Donec tincidunt velit non bibendum gravida. Cras accumsan tincidunt ornare. Donec hendrerit consequat tellus blandit accumsan. Aenean aliquam metus at arcu elementum dignissim."),
					tuple(Map.of("category", "header_3", "title", "Header 3"),
							"Aenean eu leo eu nibh tristique posuere quis quis massa."));
	}

	@Test
	void testDocumentDividedViaHorizontalRules() {
		MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
			.withHorizontalRuleCreateDocument(true)
			.build();

		MarkdownDocumentReader reader = new MarkdownDocumentReader("classpath:/horizontal-rules.md", config);

		List<Document> documents = reader.get();

		assertThat(documents).hasSize(7)
			.extracting(Document::getMetadata, Document::getText)
			.containsOnly(tuple(Map.of(),
					"Lorem ipsum dolor sit amet, consectetur adipiscing elit. Donec tincidunt velit non bibendum gravida."),
					tuple(Map.of(),
							"Cras accumsan tincidunt ornare. Donec hendrerit consequat tellus blandit accumsan. Aenean aliquam metus at arcu elementum dignissim."),
					tuple(Map.of(),
							"Nullam nisi dui, egestas nec sem nec, interdum lobortis enim. Pellentesque odio orci, faucibus eu luctus nec, venenatis et magna."),
					tuple(Map.of(),
							"Vestibulum nec eros non felis fermentum posuere eget ac risus. Curabitur et fringilla massa. Cras facilisis nec nisl sit amet sagittis."),
					tuple(Map.of(),
							"Aenean eu leo eu nibh tristique posuere quis quis massa. Nullam lacinia luctus sem ut vehicula."),
					tuple(Map.of(),
							"Aenean quis vulputate mi. Vestibulum ante ipsum primis in faucibus orci luctus et ultrices posuere cubilia curae; Nam tincidunt nunc a tortor tincidunt, nec lobortis diam rhoncus."),
					tuple(Map.of(), "Nulla facilisi. Phasellus eget tellus sed nibh ornare interdum eu eu mi."));
	}

	@Test
	void testDocumentNotDividedViaHorizontalRulesWhenIsDisabled() {
		MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
			.withHorizontalRuleCreateDocument(false)
			.build();

		MarkdownDocumentReader reader = new MarkdownDocumentReader("classpath:/horizontal-rules.md", config);

		List<Document> documents = reader.get();

		assertThat(documents).hasSize(1);

		Document documentsFirst = documents.get(0);
		assertThat(documentsFirst.getMetadata()).isEmpty();
		assertThat(documentsFirst.getText()).startsWith("Lorem ipsum dolor sit amet, consectetur adipiscing elit")
			.endsWith("Phasellus eget tellus sed nibh ornare interdum eu eu mi.");
	}

	@Test
	void testSimpleMarkdownDocumentWithHardAndSoftLineBreaks() {
		MarkdownDocumentReader reader = new MarkdownDocumentReader("classpath:/simple.md");

		List<Document> documents = reader.get();

		assertThat(documents).hasSize(1);

		Document documentsFirst = documents.get(0);
		assertThat(documentsFirst.getMetadata()).isEmpty();
		assertThat(documentsFirst.getText()).isEqualTo(
				"Lorem ipsum dolor sit amet, consectetur adipiscing elit. Donec tincidunt velit non bibendum gravida. Cras accumsan tincidunt ornare. Donec hendrerit consequat tellus blandit accumsan. Aenean aliquam metus at arcu elementum dignissim.Nullam nisi dui, egestas nec sem nec, interdum lobortis enim. Pellentesque odio orci, faucibus eu luctus nec, venenatis et magna. Vestibulum nec eros non felis fermentum posuere eget ac risus.Aenean eu leo eu nibh tristique posuere quis quis massa. Nullam lacinia luctus sem ut vehicula.");
	}

	@Test
	void testCode() {
		MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
			.withHorizontalRuleCreateDocument(true)
			.build();

		MarkdownDocumentReader reader = new MarkdownDocumentReader("classpath:/code.md", config);

		List<Document> documents = reader.get();

		assertThat(documents).satisfiesExactly(document -> {
			assertThat(document.getMetadata()).isEqualTo(Map.of());
			assertThat(document.getText()).isEqualTo("This is a Java sample application:");
		}, document -> {
			assertThat(document.getMetadata()).isEqualTo(Map.of("lang", "java", "category", "code_block"));
			assertThat(document.getText()).startsWith("package com.example.demo;")
				.contains("SpringApplication.run(DemoApplication.class, args);");
		}, document -> {
			assertThat(document.getMetadata()).isEqualTo(Map.of("category", "code_inline"));
			assertThat(document.getText()).isEqualTo(
					"Markdown also provides the possibility to use inline code formatting throughout the entire sentence.");
		}, document -> {
			assertThat(document.getMetadata()).isEqualTo(Map.of());
			assertThat(document.getText())
				.isEqualTo("Another possibility is to set block code without specific highlighting:");
		}, document -> {
			assertThat(document.getMetadata()).isEqualTo(Map.of("lang", "", "category", "code_block"));
			assertThat(document.getText()).isEqualTo("./mvnw spring-javaformat:apply\n");
		});
	}

	@Test
	void testCodeWhenCodeBlockShouldNotBeSeparatedDocument() {
		MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
			.withHorizontalRuleCreateDocument(true)
			.withIncludeCodeBlock(true)
			.build();

		MarkdownDocumentReader reader = new MarkdownDocumentReader("classpath:/code.md", config);

		List<Document> documents = reader.get();

		assertThat(documents).satisfiesExactly(document -> {
			assertThat(document.getMetadata()).isEqualTo(Map.of("lang", "java", "category", "code_block"));
			assertThat(document.getText()).startsWith("This is a Java sample application: package com.example.demo")
				.contains("SpringApplication.run(DemoApplication.class, args);");
		}, document -> {
			assertThat(document.getMetadata()).isEqualTo(Map.of("category", "code_inline"));
			assertThat(document.getText()).isEqualTo(
					"Markdown also provides the possibility to use inline code formatting throughout the entire sentence.");
		}, document -> {
			assertThat(document.getMetadata()).isEqualTo(Map.of("lang", "", "category", "code_block"));
			assertThat(document.getText()).isEqualTo(
					"Another possibility is to set block code without specific highlighting: ./mvnw spring-javaformat:apply\n");
		});
	}

	@Test
	void testBlockquote() {
		MarkdownDocumentReader reader = new MarkdownDocumentReader("classpath:/blockquote.md");

		List<Document> documents = reader.get();

		assertThat(documents).hasSize(2)
			.extracting(Document::getMetadata, Document::getText)
			.containsOnly(tuple(Map.of(),
					"Lorem ipsum dolor sit amet, consectetur adipiscing elit. Curabitur diam eros, laoreet sit amet cursus vitae, varius sed nisi. Cras sit amet quam quis velit commodo porta consectetur id nisi. Phasellus tincidunt pulvinar augue."),
					tuple(Map.of("category", "blockquote"),
							"Proin vel laoreet leo, sed luctus augue. Sed et ligula commodo, commodo lacus at, consequat turpis. Maecenas eget sapien odio. Maecenas urna lectus, pellentesque in accumsan aliquam, congue eu libero. Ut rhoncus nec justo a porttitor. Pellentesque auctor pharetra eros, viverra sodales lorem aliquet id. Curabitur semper nisi vel sem interdum suscipit."));
	}

	@Test
	void testBlockquoteWhenBlockquoteShouldNotBeSeparatedDocument() {
		MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
			.withIncludeBlockquote(true)
			.build();

		MarkdownDocumentReader reader = new MarkdownDocumentReader("classpath:/blockquote.md", config);

		List<Document> documents = reader.get();

		assertThat(documents).hasSize(1);

		Document documentsFirst = documents.get(0);
		assertThat(documentsFirst.getMetadata()).isEqualTo(Map.of("category", "blockquote"));
		assertThat(documentsFirst.getText()).isEqualTo(
				"Lorem ipsum dolor sit amet, consectetur adipiscing elit. Curabitur diam eros, laoreet sit amet cursus vitae, varius sed nisi. Cras sit amet quam quis velit commodo porta consectetur id nisi. Phasellus tincidunt pulvinar augue. Proin vel laoreet leo, sed luctus augue. Sed et ligula commodo, commodo lacus at, consequat turpis. Maecenas eget sapien odio. Maecenas urna lectus, pellentesque in accumsan aliquam, congue eu libero. Ut rhoncus nec justo a porttitor. Pellentesque auctor pharetra eros, viverra sodales lorem aliquet id. Curabitur semper nisi vel sem interdum suscipit.");
	}

	@Test
	void testLists() {
		MarkdownDocumentReader reader = new MarkdownDocumentReader("classpath:/lists.md");

		List<Document> documents = reader.get();

		assertThat(documents).hasSize(2)
			.extracting(Document::getMetadata, Document::getText)
			.containsOnly(tuple(Map.of("category", "header_2", "title", "Ordered list"),
					"Lorem ipsum dolor sit amet, consectetur adipiscing elit. Curabitur diam eros, laoreet sit amet cursus vitae, varius sed nisi. Cras sit amet quam quis velit commodo porta consectetur id nisi. Phasellus tincidunt pulvinar augue. Proin vel laoreet leo, sed luctus augue. Sed et ligula commodo, commodo lacus at, consequat turpis. Maecenas eget sapien odio. Pellentesque auctor pharetra eros, viverra sodales lorem aliquet id. Curabitur semper nisi vel sem interdum suscipit. Maecenas urna lectus, pellentesque in accumsan aliquam, congue eu libero. Ut rhoncus nec justo a porttitor."),
					tuple(Map.of("category", "header_2", "title", "Unordered list"),
							"Aenean eu leo eu nibh tristique posuere quis quis massa. Aenean imperdiet libero dui, nec malesuada dui maximus vel. Vestibulum sed dui condimentum, cursus libero in, dapibus tortor. Etiam facilisis enim in egestas dictum."));
	}

	@Test
	void testWithAdditionalMetadata() {
		MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
			.withAdditionalMetadata("service", "some-service-name")
			.withAdditionalMetadata("env", "prod")
			.build();

		MarkdownDocumentReader reader = new MarkdownDocumentReader("classpath:/simple.md", config);

		List<Document> documents = reader.get();

		assertThat(documents).hasSize(1);

		Document documentsFirst = documents.get(0);
		assertThat(documentsFirst.getMetadata()).isEqualTo(Map.of("service", "some-service-name", "env", "prod"));
		assertThat(documentsFirst.getText()).startsWith("Lorem ipsum dolor sit amet, consectetur adipiscing elit.");
	}

	@Test
	void testLinksAndImagesPreserved() {
		MarkdownDocumentReader reader = new MarkdownDocumentReader("classpath:/links.md");
		List<Document> documents = reader.get();

		assertThat(documents).hasSize(1);

		Document document = documents.get(0);
		assertThat(document.getText()).contains("Example Java (docs/src/doc/docs/assets/img/example-java.png)")
			.contains("Example (https://example.com/example-project)")
			.contains("Alt text (https://example.com/image.png)")
			.contains("Alt text for image (https://example.com/image2.png) (https://example.com/linked)")
			.contains("https://example.com/self")
			.doesNotContain("https://example.com/self (https://example.com/self)");
	}

	@Test
	void testTableLinksAndImagesPreserved() {
		MarkdownDocumentReader reader = new MarkdownDocumentReader("classpath:/table-links.md");
		List<Document> documents = reader.get();

		assertThat(documents).hasSize(1);

		Document document = documents.get(0);
		assertThat(document.getText()).contains("Table link (https://example.com/table-link)")
			.contains("Table image (https://example.com/table-image.png)");
	}

}
