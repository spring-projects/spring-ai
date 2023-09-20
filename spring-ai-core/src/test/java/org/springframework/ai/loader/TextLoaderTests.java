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

package org.springframework.ai.loader;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.document.Document;
import org.springframework.ai.loader.impl.TextLoader;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 */
public class TextLoaderTests {

	private Resource resource = new DefaultResourceLoader().getResource("classpath:text_source.txt");

	@Test
	void loadText() {
		assertThat(resource).isNotNull();
		TextLoader textLoader = new TextLoader(resource);
		textLoader.getCustomMetadata().put("customKey", "Value");

		List<Document> documents = textLoader.load();

		assertThat(documents.size()).isEqualTo(54);

		for (Document document : documents) {
			assertThat(document.getMetadata().get("customKey")).isEqualTo("Value");
			assertThat(document.getMetadata().get(TextLoader.SOURCE_METADATA)).isEqualTo("text_source.txt");
			assertThat(document.getMetadata().get(TextLoader.CHARSET_METADATA)).isEqualTo("UTF-8");
			assertThat(document.getContent()).isNotEmpty();
		}
	}

}