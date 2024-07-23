package org.springframework.ai.reader.markdown;

import org.commonmark.node.*;
import org.commonmark.parser.Parser;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the given Markdown resource and groups headers, paragraphs, or text divided by
 * horizontal lines (depending on the
 * {@link MarkdownDocumentReaderConfig#horizontalRuleCreateDocument} configuration) into
 * {@link Document}s.
 *
 * @author Piotr Olaszewski
 */
public class MarkdownDocumentReader implements DocumentReader {

	/**
	 * The resource points to the Markdown document.
	 */
	private final Resource markdownResource;

	/**
	 * Configuration to a parsing process.
	 */
	private final MarkdownDocumentReaderConfig config;

	/**
	 * Markdown parser.
	 */
	private final Parser parser;

	public MarkdownDocumentReader(String markdownResource) {
		this(new DefaultResourceLoader().getResource(markdownResource), MarkdownDocumentReaderConfig.defaultConfig());
	}

	public MarkdownDocumentReader(String markdownResource, MarkdownDocumentReaderConfig config) {
		this(new DefaultResourceLoader().getResource(markdownResource), config);
	}

	public MarkdownDocumentReader(Resource markdownResource, MarkdownDocumentReaderConfig config) {
		this.markdownResource = markdownResource;
		this.config = config;
		this.parser = Parser.builder().build();
	}

	/**
	 * Extracts and returns a list of documents from the resource.
	 * @return List of extracted {@link Document}
	 */
	@Override
	public List<Document> get() {
		try (var input = markdownResource.getInputStream()) {
			Node node = parser.parseReader(new InputStreamReader(input));

			DocumentVisitor documentVisitor = new DocumentVisitor(config);
			node.accept(documentVisitor);

			return documentVisitor.getDocuments();
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * A convenient class for visiting handled nodes in the Markdown document.
	 */
	static class DocumentVisitor extends AbstractVisitor {

		private final List<Document> documents = new ArrayList<>();

		private final List<String> currentParagraphs = new ArrayList<>();

		private final MarkdownDocumentReaderConfig config;

		private Document.Builder currentDocumentBuilder;

		public DocumentVisitor(MarkdownDocumentReaderConfig config) {
			this.config = config;
		}

		@Override
		public void visit(org.commonmark.node.Document document) {
			currentDocumentBuilder = Document.builder();
			super.visit(document);
		}

		@Override
		public void visit(Heading heading) {
			buildAndFlush();
			super.visit(heading);
		}

		@Override
		public void visit(ThematicBreak thematicBreak) {
			if (config.horizontalRuleCreateDocument) {
				buildAndFlush();
			}
			super.visit(thematicBreak);
		}

		@Override
		public void visit(SoftLineBreak softLineBreak) {
			translateLineBreakToSpace();
			super.visit(softLineBreak);
		}

		@Override
		public void visit(HardLineBreak hardLineBreak) {
			translateLineBreakToSpace();
			super.visit(hardLineBreak);
		}

		@Override
		public void visit(ListItem listItem) {
			translateLineBreakToSpace();
			super.visit(listItem);
		}

		@Override
		public void visit(BlockQuote blockQuote) {
			if (!config.includeBlockquote) {
				buildAndFlush();
			}

			translateLineBreakToSpace();
			currentDocumentBuilder.withMetadata("category", "blockquote");
			super.visit(blockQuote);
		}

		@Override
		public void visit(Code code) {
			currentParagraphs.add(code.getLiteral());
			currentDocumentBuilder.withMetadata("category", "code_inline");
			super.visit(code);
		}

		@Override
		public void visit(FencedCodeBlock fencedCodeBlock) {
			if (!config.includeCodeBlock) {
				buildAndFlush();
			}

			translateLineBreakToSpace();
			currentParagraphs.add(fencedCodeBlock.getLiteral());
			currentDocumentBuilder.withMetadata("category", "code_block");
			currentDocumentBuilder.withMetadata("lang", fencedCodeBlock.getInfo());

			buildAndFlush();

			super.visit(fencedCodeBlock);
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

				Document.Builder builder = currentDocumentBuilder.withContent(content);

				config.additionalMetadata.forEach(builder::withMetadata);

				Document document = builder.build();

				documents.add(document);

				currentParagraphs.clear();
			}
			currentDocumentBuilder = Document.builder();
		}

		private void translateLineBreakToSpace() {
			if (!currentParagraphs.isEmpty()) {
				currentParagraphs.add(" ");
			}
		}

	}

}
