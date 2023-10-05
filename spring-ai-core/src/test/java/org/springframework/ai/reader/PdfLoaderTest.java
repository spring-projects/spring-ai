package org.springframework.ai.loader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.loader.impl.PdfLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class PdfLoaderTest {

	private Resource resource;

	private PdfLoader pdfLoader;

	@BeforeEach
	public void setUp() {
		resource = new ClassPathResource("pdf_source.pdf");
		pdfLoader = new PdfLoader(resource);
	}

	@Test
	public void testLoad() {
		List<Document> documents = pdfLoader.load();

		assertEquals(1, documents.size());
		Document document = documents.get(0);
		assertFalse(document.getText().isEmpty());
		assertEquals("UTF-8", document.getMetadata().get(PdfLoader.CHARSET_METADATA));
		assertEquals("pdf_source.pdf", document.getMetadata().get(PdfLoader.SOURCE_METADATA));
	}

}
