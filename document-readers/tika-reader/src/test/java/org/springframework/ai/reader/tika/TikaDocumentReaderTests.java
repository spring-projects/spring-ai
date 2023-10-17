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

package org.springframework.ai.reader.tika;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 */
public class TikaDocumentReaderTests {

	@ParameterizedTest
	@CsvSource({ "word-sample.docx,Two kinds of links are possible, those that refer to an external website",
			"word-sample.doc,The limited permissions granted above are perpetual and will not be revoked by OASIS or its successors or assigns.",
			"sample2.pdf,Consult doc/pdftex/manual.pdf from your tetex distribution for more",
			"sample.ppt,Sed ipsum tortor, fringilla a consectetur eget, cursus posuere sem.",
			"sample.pptx,Lorem ipsum dolor sit amet, consectetur adipiscing elit." })
	public void testDocx(String fileName, String contentSnipped) {

		var docs = new TikaDocumentReader("classpath:/" + fileName).get();
		assertThat(docs).hasSize(1);

		var doc = docs.get(0);

		assertThat(doc.getMetadata()).containsKeys(TikaDocumentReader.METADATA_FILE_NAME);
		assertThat(doc.getMetadata().get(TikaDocumentReader.METADATA_FILE_NAME)).isEqualTo(fileName);
		assertThat(doc.getContent()).contains(contentSnipped);
	}

}
