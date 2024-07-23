package org.springframework.ai.reader.markdown;

import org.commonmark.node.*;
import org.commonmark.parser.Parser;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the given Markdown resource and groups headers paragraphs into
 * {@link Document}'s.
 *
 * @author Piotr Olaszewski
 */
public class MarkdownDocumentReader implements DocumentReader {

	private final Resource markdownResource;

	private final Parser parser;

	public MarkdownDocumentReader(String markdownResource) {
		this(new DefaultResourceLoader().getResource(markdownResource));
	}

	public MarkdownDocumentReader(Resource markdownResource) {
		this.markdownResource = markdownResource;
		this.parser = Parser.builder().build();
	}

	@Override
	public List<Document> get() {
		try (var input = markdownResource.getInputStream()) {
			Node node = parser.parseReader(new InputStreamReader(input));

			DocumentVisitor documentVisitor = new DocumentVisitor();
			node.accept(documentVisitor);

			return documentVisitor.getDocuments();
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	static class DocumentVisitor extends AbstractVisitor {

		private final List<Document> documents = new ArrayList<>();

		private final List<String> currentParagraphs = new ArrayList<>();

		private Document.Builder currentDocumentBuilder;

		@Override
		public void visit(Heading heading) {
			buildAndFlush();

			currentDocumentBuilder = Document.builder();

			super.visit(heading);
		}

		@Override
		public void visit(SoftLineBreak softLineBreak) {
			if (!currentParagraphs.isEmpty()) {
				currentParagraphs.add(" ");
			}
			super.visit(softLineBreak);
		}

		@Override
		public void visit(Text text) {
			if (text.getParent() instanceof Heading heading) {
				currentDocumentBuilder.withMetadata("category", "header_%d".formatted(heading.getLevel()))
					.withMetadata("title", text.getLiteral());
			}
			else {
				currentParagraphs.add(text.getLiteral());
			}

			super.visit(text);
		}

		public List<Document> getDocuments() {
			buildAndFlush();

			return documents;
		}

		private void buildAndFlush() {
			if (!currentParagraphs.isEmpty()) {
				String content = String.join("", currentParagraphs);

				Document document = currentDocumentBuilder.withContent(content).build();
				documents.add(document);

				currentParagraphs.clear();
			}
		}

	}

}
