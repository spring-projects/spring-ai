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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.Code;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.Text;
import org.commonmark.node.ThematicBreak;
import org.commonmark.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;

/**
 * Reads the given Markdown resource and groups headers, paragraphs, or text divided by
 * horizontal lines (depending on the
 * {@link MarkdownDocumentReaderConfig#horizontalRuleCreateDocument} configuration) into
 * {@link Document}s. Currently, only Markdown resource files in the ClassPath path are
 * supported, and Markdown files can be read in the way of directory path configuration.
 * Use
 * {@See org.springframework.ai.reader.markdown.MarkdownDocumentReaderTest#testDirPathSingle()}
 * {@See org.springframework.ai.reader.markdown.MarkdownDocumentReaderTest#testMultipleMarkdownFiles()}
 *
 * @author Piotr Olaszewski
 * @auther shown.Ji
 */
public class MarkdownDocumentReader implements DocumentReader {

	private final static Logger logger = LoggerFactory.getLogger(MarkdownDocumentReader.class);

	/**
	 * The resource points to the Markdown document.
	 */
	private final List<Resource> markdownResources;

	/**
	 * Configuration to a parsing process.
	 */
	private final MarkdownDocumentReaderConfig config;

	/**
	 * Markdown parser.
	 */
	private final Parser parser;

	/**
	 * Create a new {@link MarkdownDocumentReader} instance.
	 * @param markdownResourcePath the markdown file resource path to read
	 */
	public MarkdownDocumentReader(String markdownResourcePath) {
		this(loadResources(loadResourcePaths(markdownResourcePath)), MarkdownDocumentReaderConfig.defaultConfig());
	}

	/**
	 * Create a new {@link MarkdownDocumentReader} instance.
	 * @param markdownResourcePath the resource path
	 * @param config the configuration to use
	 */
	public MarkdownDocumentReader(String markdownResourcePath, MarkdownDocumentReaderConfig config) {
		this(loadResources(loadResourcePaths(markdownResourcePath)), config);
	}

	/**
	 * Create a new {@link MarkdownDocumentReader} instance.
	 * @param markdownResourcePaths the resources paths to read
	 */
	public MarkdownDocumentReader(List<String> markdownResourcePaths) {
		this(loadResources(markdownResourcePaths), MarkdownDocumentReaderConfig.defaultConfig());
	}

	/**
	 * Create a new {@link MarkdownDocumentReader} instance.
	 * @param markdownResource the markdown file resources to read
	 */
	public MarkdownDocumentReader(Resource markdownResource) {
		this(markdownResource, MarkdownDocumentReaderConfig.defaultConfig());
	}

	/**
	 * Create a new {@link MarkdownDocumentReader} instance.
	 * @param markdownResource the markdown file resource to read
	 * @param config the configuration to use
	 */
	public MarkdownDocumentReader(Resource markdownResource, MarkdownDocumentReaderConfig config) {
		this(List.of(markdownResource), config);
	}

	/**
	 * Create a new {@link MarkdownDocumentReader} instance.
	 * @param markdownResource the resource to read
	 * @param config the configuration to use
	 */
	public MarkdownDocumentReader(List<Resource> markdownResource, MarkdownDocumentReaderConfig config) {

		Assert.notEmpty(markdownResource, "Markdown resource must not be empty");

		this.markdownResources = markdownResource;
		this.config = config;
		this.parser = Parser.builder().build();
	}

	/**
	 * Extracts and returns a list of documents from the resource.
	 * @return List of extracted {@link Document}
	 */
	@Override
	public List<Document> get() {

		return this.markdownResources.stream()
			.flatMap(markdownResource -> getDocuments(markdownResource).stream())
			.collect(Collectors.toList());
	}

	private List<Document> getDocuments(Resource markdownResource) {

		List<Document> documents;
		try {
			if (markdownResource.isFile() && !markdownResource.exists()) {
				throw new FileNotFoundException("Resource does not exist: " + markdownResource.getFilename());
			}

			logger.debug("Attempting to read resource: " + markdownResource.getDescription());
			try (InputStream input = markdownResource.getInputStream()) {
				Node node = this.parser.parseReader(new InputStreamReader(input));

				DocumentVisitor documentVisitor = new DocumentVisitor(this.config);
				node.accept(documentVisitor);

				documents = documentVisitor.getDocuments();
			}
		}
		catch (IOException e) {
			logger.error("Error reading markdown resource: " + e.getMessage(), e);
			throw new RuntimeException("Error reading markdown resource", e);
		}
		return documents;
	}

	/**
	 * A convenient class for visiting handled nodes in the Markdown document.
	 */
	static class DocumentVisitor extends AbstractVisitor {

		private final List<Document> documents = new ArrayList<>();

		private final List<String> currentParagraphs = new ArrayList<>();

		private final MarkdownDocumentReaderConfig config;

		private Document.Builder currentDocumentBuilder;

		DocumentVisitor(MarkdownDocumentReaderConfig config) {
			this.config = config;
		}

		/**
		 * Visits the document node and initializes the current document builder.
		 */
		@Override
		public void visit(org.commonmark.node.Document document) {
			this.currentDocumentBuilder = Document.builder();
			super.visit(document);
		}

		@Override
		public void visit(Heading heading) {
			buildAndFlush();
			super.visit(heading);
		}

		@Override
		public void visit(ThematicBreak thematicBreak) {
			if (this.config.horizontalRuleCreateDocument) {
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
			if (!this.config.includeBlockquote) {
				buildAndFlush();
			}

			translateLineBreakToSpace();
			this.currentDocumentBuilder.metadata("category", "blockquote");
			super.visit(blockQuote);
		}

		@Override
		public void visit(Code code) {
			this.currentParagraphs.add(code.getLiteral());
			this.currentDocumentBuilder.metadata("category", "code_inline");
			super.visit(code);
		}

		@Override
		public void visit(FencedCodeBlock fencedCodeBlock) {
			if (!this.config.includeCodeBlock) {
				buildAndFlush();
			}

			translateLineBreakToSpace();
			this.currentParagraphs.add(fencedCodeBlock.getLiteral());
			this.currentDocumentBuilder.metadata("category", "code_block");
			this.currentDocumentBuilder.metadata("lang", fencedCodeBlock.getInfo());

			buildAndFlush();

			super.visit(fencedCodeBlock);
		}

		@Override
		public void visit(Text text) {
			if (text.getParent() instanceof Heading heading) {
				this.currentDocumentBuilder.metadata("category", "header_%d".formatted(heading.getLevel()))
					.metadata("title", text.getLiteral());
			}
			else {
				this.currentParagraphs.add(text.getLiteral());
			}

			super.visit(text);
		}

		public List<Document> getDocuments() {
			buildAndFlush();

			return this.documents;
		}

		private void buildAndFlush() {
			if (!this.currentParagraphs.isEmpty()) {
				String content = String.join("", this.currentParagraphs);

				Document.Builder builder = this.currentDocumentBuilder.text(content);

				this.config.additionalMetadata.forEach(builder::metadata);

				Document document = builder.build();

				this.documents.add(document);

				this.currentParagraphs.clear();
			}
			this.currentDocumentBuilder = Document.builder();
		}

		private void translateLineBreakToSpace() {
			if (!this.currentParagraphs.isEmpty()) {
				this.currentParagraphs.add(" ");
			}
		}

	}

	/**
	 * Load resources from the given paths.
	 * @param markdownResourcePaths the resource paths to load
	 * @return a list of Resources
	 */
	private static List<Resource> loadResources(List<String> markdownResourcePaths) {

		DefaultResourceLoader resourceLoader = new DefaultResourceLoader();

		return markdownResourcePaths.stream().map(resourceLoader::getResource).collect(Collectors.toList());
	}

	/**
	 * Load resource paths from the given path.
	 * @param resourcePath markdown resource path
	 * @return a list of resource paths
	 */
	private static List<String> loadResourcePaths(String resourcePath) {
		List<String> resources = new ArrayList<>();

		if (resourcePath.startsWith("classpath:")) {
			String path = resourcePath.replace("classpath:", "");
			URL resourceURL = MarkdownDocumentReader.class.getResource(path);

			if (resourceURL != null) {
				File file = new File(resourceURL.getFile());
				if (file.isDirectory()) {
					File[] files = file.listFiles((dir, name) -> name.endsWith(".md"));
					if (files != null) {
						for (File mdFile : files) {
							resources.add("classpath:" + mdFile.getName());
						}
					}
				}
				else if (file.exists() && file.getName().endsWith(".md")) {
					resources.add(resourcePath);
				}
			}
		}
		else {
			File file = new File(resourcePath);
			if (file.exists() && file.isDirectory()) {
				File[] files = file.listFiles((dir, name) -> name.endsWith(".md"));
				if (files != null) {
					for (File mdFile : files) {
						resources.add(mdFile.getAbsolutePath());
					}
				}
			}
			else if (file.exists() && file.getName().endsWith(".md")) {
				resources.add(file.getAbsolutePath());
			}
		}

		return resources;
	}

}
