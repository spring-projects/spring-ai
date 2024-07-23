package org.springframework.ai.reader.markdown;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

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
					"Lorem ipsum dolor sit amet, consectetur adipiscing elit. Curabitur diam eros, laoreet sit amet cursus vitae, varius sednisi. Cras sit amet quam quis velit commodo porta consectetur id nisi. Phasellus tincidunt pulvinar augue."),
					tuple(Map.of("category", "header_1", "title", "Header 1b"),
							"Vestibulum ante ipsum primis in faucibus orci luctus et ultrices posuere cubilia curae; Etiam lobortis risus libero, sedsollicitudin risus cursus in. Morbi enim metus, ornare vel lacinia eget, venenatis vel nibh."),
					tuple(Map.of("category", "header_2", "title", "Header 2b"),
							"Proin vel laoreet leo, sed luctus augue. Sed et ligula commodo, commodo lacus at, consequat turpis. Maecenas eget sapienodio. Maecenas urna lectus, pellentesque in accumsan aliquam, congue eu libero."),
					tuple(Map.of("category", "header_2", "title", "Header 2c"),
							"Ut rhoncus nec justo a porttitor. Pellentesque auctor pharetra eros, viverra sodales lorem aliquet id. Curabitur semper nisi vel sem interdum suscipit."));
	}

}
