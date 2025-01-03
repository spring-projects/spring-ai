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

package org.springframework.ai.reader.tika;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 */
public class TikaDocumentReaderTests {

	@ParameterizedTest
	@CsvSource({
			"classpath:/word-sample.docx,word-sample.docx,Two kinds of links are possible, those that refer to an external website",
			"classpath:/word-sample.doc,word-sample.doc,The limited permissions granted above are perpetual and will not be revoked by OASIS",
			"classpath:/sample2.pdf,sample2.pdf,Consult doc/pdftex/manual.pdf from your tetex distribution for more",
			"classpath:/sample.ppt,sample.ppt,Sed ipsum tortor, fringilla a consectetur eget, cursus posuere sem.",
			"classpath:/sample.pptx,sample.pptx,Lorem ipsum dolor sit amet, consectetur adipiscing elit.",
			"https://github.com/spring-projects/spring-ai/,https://github.com/spring-projects/spring-ai/,An Application Framework for AI Engineering" })
	public void testDocx(String resourceUri, String resourceName, String contentSnipped) {

		var docs = new TikaDocumentReader(resourceUri).get();
		assertThat(docs).hasSize(1);

		var doc = docs.get(0);

		assertThat(doc.getMetadata()).containsKeys(TikaDocumentReader.METADATA_SOURCE);
		assertThat(doc.getMetadata().get(TikaDocumentReader.METADATA_SOURCE)).isEqualTo(resourceName);
		assertThat(doc.getText()).contains(contentSnipped);
	}

}
