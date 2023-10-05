package org.springframework.ai.reader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.PdfReader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class PdfReaderTest {

	private Resource resource;

	private PdfReader pdfLoader;

	@BeforeEach
	public void setUp() {
		resource = new ClassPathResource("pdf_source.pdf");
		pdfLoader = new PdfReader(resource);
	}

	@Test
	public void testLoad() {
		List<Document> documents = pdfLoader.get();

		assertEquals(1, documents.size());
		Document document = documents.get(0);
		assertFalse(document.getContent().isEmpty());
		assertEquals("UTF-8", document.getMetadata().get(PdfReader.CHARSET_METADATA));
		assertEquals("pdf_source.pdf", document.getMetadata().get(PdfReader.SOURCE_METADATA));
	}

}
