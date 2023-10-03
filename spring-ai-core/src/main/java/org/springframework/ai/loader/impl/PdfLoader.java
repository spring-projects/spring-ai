package org.springframework.ai.loader.impl;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.ai.document.Document;
import org.springframework.ai.loader.Loader;
import org.springframework.ai.splitter.TextSplitter;
import org.springframework.ai.splitter.TokenTextSplitter;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class PdfLoader implements Loader {

	public static final String CHARSET_METADATA = "charset";

	public static final String SOURCE_METADATA = "source";

	private final Resource resource;

	private Charset charset = StandardCharsets.UTF_8;

	private Map<String, Object> customMetadata = new HashMap<>();

	public PdfLoader(String resourceUrl) {
		this(new DefaultResourceLoader().getResource(resourceUrl));
	}

	public PdfLoader(Resource resource) {
		Objects.requireNonNull(resource, "The Spring Resource must not be null");
		this.resource = resource;
	}

	public void setCharset(Charset charset) {
		Objects.requireNonNull(charset, "The charset must not be null");
		this.charset = charset;
	}

	public Charset getCharset() {
		return this.charset;
	}

	public Map<String, Object> getCustomMetadata() {
		return this.customMetadata;
	}

	@Override
	public List<Document> load() {
		return load(new TokenTextSplitter());
	}

	@Override
	public List<Document> load(TextSplitter textSplitter) {
		try {
			PDDocument pdfDocument = PDDocument.load(this.resource.getInputStream());
			PDFTextStripper pdfStripper = new PDFTextStripper();
			String document = pdfStripper.getText(pdfDocument);

			this.customMetadata.put(CHARSET_METADATA, this.charset.name());
			this.customMetadata.put(SOURCE_METADATA, this.resource.getFilename());

			return textSplitter.apply(Collections.singletonList(new Document(document, this.customMetadata)));
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

}
