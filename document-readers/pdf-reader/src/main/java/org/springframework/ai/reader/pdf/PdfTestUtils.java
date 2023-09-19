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

package org.springframework.ai.reader.pdf;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import org.springframework.ai.document.Document;

/**
 * @author Christian Tzolov
 */
public class PdfTestUtils {

	public static void writeToFile(String fileName, List<Document> docs, boolean withDocumentMarkers)
			throws IOException {
		var writer = new FileWriter(fileName, false);

		int i = 0;
		for (Document doc : docs) {
			if (withDocumentMarkers) {
				writer.write(String.format("\n### Doc: %s, pages:[%s,%s]\n", i,
						doc.getMetadata().get(PagePdfDocumentReader.METADATA_START_PAGE_NUMBER),
						doc.getMetadata().get(PagePdfDocumentReader.METADATA_END_PAGE_NUMBER)));
			}
			writer.write(doc.getContent());
			i++;
		}

		writer.close();
	}

}
