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

package org.springframework.ai.document;

import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.document.DefaultTextFormatter.MetadataMode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 */
public class TextFormatterTests {

	Document document = new Document("The World is Big and Salvation Lurks Around the Corner",
			Map.of("embedKey1", "value1", "embedKey2", "value2", "embedKey3", "value3", "llmKey2", "value4"));

	@Test
	public void defaultConfigTextFormatter() {

		DefaultTextFormatter defaultConfigFormatter = DefaultTextFormatter.defaultConfig();

		assertThat(document.getContent(defaultConfigFormatter)).isEqualTo(defaultConfigFormatter.apply(document));

		assertThat(document.getContent(defaultConfigFormatter)).isEqualTo("""
				llmKey2: value4
				embedKey1: value1
				embedKey2: value2
				embedKey3: value3

				The World is Big and Salvation Lurks Around the Corner""");
	}

	@Test
	public void customTextFormatter() {

		DefaultTextFormatter textFormatter = DefaultTextFormatter.builder()
			.withExcludedEmbedMetadataKeys("embedKey2", "embedKey3")
			.withExcludedLlmMetadataKeys("llmKey2")
			.withMetadataMode(MetadataMode.EMBED)
			.withTextTemplate("Metadata:\n{metadata_string}\n\nText:{content}")
			.withMetadataTemplate("Key/Value {key}={value}")
			.build();

		assertThat(document.getContent(textFormatter)).isEqualTo(textFormatter.apply(document));

		assertThat(document.getContent(textFormatter)).isEqualTo("""
				Metadata:
				Key/Value llmKey2=value4
				Key/Value embedKey1=value1

				Text:The World is Big and Salvation Lurks Around the Corner""");

	}

	@Test
	public void noFormatter() {
		assertThat(document.getContent()).isEqualTo(document.getContent(doc -> doc.getContent()));
		assertThat(document.getContent()).isEqualTo("""
				The World is Big and Salvation Lurks Around the Corner""");
	}

}
