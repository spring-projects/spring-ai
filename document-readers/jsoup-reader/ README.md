# Spring AI JSoup Document Reader

This module provides an HTML document reader for the Spring AI project. It leverages the [JSoup](https://jsoup.org/) library to parse HTML content and extract text and metadata, making it suitable for use in AI applications.

## Features

*   **Flexible Text Extraction:**
    *   Extract all text from the `<body>` of an HTML document.
    *   Extract text from specific elements using CSS selectors.
    *   Group text by element, creating a separate document for each selected element.
    *   Combine text from multiple selected elements using a configurable separator.
*   **Metadata Extraction:**
    *   Extract the document title.
    *   Extract content from `<meta>` tags (e.g., description, keywords).  You can specify which meta tags to extract.
    *   Extract a list of all absolute URLs of links (`<a href="...">`) within the document.
*   **Configurable:**
    *   Specify the character encoding (defaults to UTF-8).
    *   Customize the CSS selector for element selection.
    *   Configure the separator string for joining text from multiple elements.
    *   Choose whether to extract all text or use element-based extraction.
    *   Enable/disable link URL extraction.
    * Add additional metadata using configuration.
*   **Resource-Based:** Works with Spring's `Resource` abstraction, allowing you to read HTML from files, classpath resources, URLs, and even in-memory byte arrays.

---

#### How to Build:
```bash
./mvnw -pl document-readers/jsoup-reader clean install 
```