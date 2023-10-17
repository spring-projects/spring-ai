# How to configure PDF Reader


## PagePdfDocumentReader

``` java
	PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(
			"file:document-readers/pdf-reader/src/test/resources/sample.pdf",
			PdfDocumentReaderConfig.builder()
					.withPageTopMargin(0)
					.withPageBottomMargin(0)
					.withPageExtractedTextFormatter(PageExtractedTextFormatter.builder()
							.withNumberOfTopTextLinesToDelete(0)
							.withNumberOfBottomTextLinesToDelete(3)
							.withNumberOfTopPagesToSkipBeforeDelete(0)
							.build())
					.withPagesPerDocument(1)
					.build());

	var documents = pdfReader.get();

	PdfTestUtils.writeToFile("document-readers/pdf-reader/target/sample.txt", documents, false);
```

```java
	public static void main(String[] args) throws IOException {

		ParagraphPdfDocumentReader pdfReader = new ParagraphPdfDocumentReader(
				"file:document-readers/pdf-reader/src/test/resources/sample2.pdf",
				PdfDocumentReaderConfig.builder()
						// .withPageBottomMargin(15)
						// .withReversedParagraphPosition(true)
						// .withTextLeftAlignment(true)
						.build());
		// ParagraphPdfDocumentReader pdfReader = new ParagraphPdfDocumentReader(
		// "file:document-readers/pdf-reader/src/test/resources/spring-framework.pdf",
		// PdfDocumentReaderConfig.builder()
		// .withPageBottomMargin(15)
		// .withReversedParagraphPosition(true)
		// // .withTextLeftAlignment(true)
		// .build());

		// PdfDocumentReader pdfReader = new
		// PdfDocumentReader("file:document-readers/pdf-reader/src/test/resources/uber-k-10.pdf",
		// PdfDocumentReaderConfig.builder().withPageTopMargin(80).withPageBottomMargin(60).build());

		var documents = pdfReader.get();

		writeToFile("document-readers/pdf-reader/target/sample2.txt", documents, true);
		System.out.println(documents.size());

	}
```