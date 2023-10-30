/*
 * Copyright 2023-2023 the original author or authors.
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

package org.springframework.ai.writer;

import java.io.FileWriter;
import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentWriter;
import org.springframework.ai.document.MetadataMode;
import org.springframework.util.Assert;

/**
 * Writes the content of a list of {@link Document}s into a file.
 *
 * @author Christian Tzolov
 */
public class FileDocumentWriter implements DocumentWriter {

	public static final String METADATA_START_PAGE_NUMBER = "page_number";

	public static final String METADATA_END_PAGE_NUMBER = "end_page_number";

	private final String fileName;

	private final boolean withDocumentMarkers;

	private final MetadataMode metadataMode;

	private final boolean append;

	public FileDocumentWriter(String fileName) {
		this(fileName, false, MetadataMode.NONE, false);
	}

	public FileDocumentWriter(String fileName, boolean withDocumentMarkers) {
		this(fileName, withDocumentMarkers, MetadataMode.NONE, false);
	}

	/**
	 * Writes the content of a list of {@link Document}s into a file.
	 * @param fileName The name of the file to write the documents to.
	 * @param withDocumentMarkers Whether to include document markers in the output.
	 * @param metadataMode Document content formatter mode. Specifies what document
	 * content to be written to the file.
	 * @param append if {@code true}, then data will be written to the end of the file
	 * rather than the beginning.
	 */
	public FileDocumentWriter(String fileName, boolean withDocumentMarkers, MetadataMode metadataMode, boolean append) {
		Assert.hasText(fileName, "File name must have a text.");
		Assert.notNull(metadataMode, "MetadataMode must not be null.");

		this.fileName = fileName;
		this.withDocumentMarkers = withDocumentMarkers;
		this.metadataMode = metadataMode;
		this.append = append;
	}

	@Override
	public void accept(List<Document> docs) {

		try (var writer = new FileWriter(this.fileName, this.append)) {

			int index = 0;
			for (Document doc : docs) {
				if (this.withDocumentMarkers) {
					writer.write(String.format("%n### Doc: %s, pages:[%s,%s]\n", index,
							doc.getMetadata().get(METADATA_START_PAGE_NUMBER),
							doc.getMetadata().get(METADATA_END_PAGE_NUMBER)));
				}
				writer.write(doc.getFormattedContent(this.metadataMode));
				index++;
			}
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}
