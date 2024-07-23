package org.springframework.ai.reader.markdown;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

/**
 * @author Piotr Olaszewski
 */
class MarkdownDocumentReaderTest {

	@Test
	void testOnlyHeadersWithParagraphs() {
		MarkdownDocumentReader reader = new MarkdownDocumentReader("classpath:/only-headers.md");

		List<Document> documents = reader.get();

		assertThat(documents).hasSize(4)
			.extracting(Document::getMetadata, Document::getContent)
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
			.extracting(Document::getMetadata, Document::getContent)
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
			.extracting(Document::getMetadata, Document::getContent)
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
		assertThat(documentsFirst.getContent()).startsWith("Lorem ipsum dolor sit amet, consectetur adipiscing elit")
			.endsWith("Phasellus eget tellus sed nibh ornare interdum eu eu mi.");
	}

}
