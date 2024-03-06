/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 */
public class ContentFormatterTests {

	Document document = new Document("The World is Big and Salvation Lurks Around the Corner",
			Map.of("embedKey1", "value1", "embedKey2", "value2", "embedKey3", "value3", "llmKey2", "value4"));

	@Test
	public void noExplicitlySetFormatter() {
		assertThat(document.getContent()).isEqualTo("""
				The World is Big and Salvation Lurks Around the Corner""");

		assertThat(document.getFormattedContent()).isEqualTo(document.getFormattedContent(MetadataMode.ALL));
		assertThat(document.getFormattedContent())
			.isEqualTo(document.getFormattedContent(Document.DEFAULT_CONTENT_FORMATTER, MetadataMode.ALL));

	}

	@Test
	public void defaultConfigTextFormatter() {

		DefaultContentFormatter defaultConfigFormatter = DefaultContentFormatter.defaultConfig();

		assertThat(document.getFormattedContent(defaultConfigFormatter, MetadataMode.ALL)).isEqualTo("""
				llmKey2: value4
				embedKey1: value1
				embedKey2: value2
				embedKey3: value3

				The World is Big and Salvation Lurks Around the Corner""");

		assertThat(document.getFormattedContent(defaultConfigFormatter, MetadataMode.ALL))
			.isEqualTo(document.getFormattedContent());

		assertThat(document.getFormattedContent(defaultConfigFormatter, MetadataMode.ALL))
			.isEqualTo(defaultConfigFormatter.format(document, MetadataMode.ALL));
	}

	@Test
	public void customTextFormatter() {

		DefaultContentFormatter textFormatter = DefaultContentFormatter.builder()
			.withExcludedEmbedMetadataKeys("embedKey2", "embedKey3")
			.withExcludedInferenceMetadataKeys("llmKey2")
			.withTextTemplate("Metadata:\n{metadata_string}\n\nText:{content}")
			.withMetadataTemplate("Key/Value {key}={value}")
			.build();

		assertThat(document.getFormattedContent(textFormatter, MetadataMode.EMBED)).isEqualTo("""
				Metadata:
				Key/Value llmKey2=value4
				Key/Value embedKey1=value1

				Text:The World is Big and Salvation Lurks Around the Corner""");

		assertThat(document.getContent()).isEqualTo("""
				The World is Big and Salvation Lurks Around the Corner""");

		assertThat(document.getFormattedContent(textFormatter, MetadataMode.EMBED))
			.isEqualTo(textFormatter.format(document, MetadataMode.EMBED));

		var documentWithCustomFormatter = new Document(document.getId(), document.getContent(), document.getMetadata());
		documentWithCustomFormatter.setContentFormatter(textFormatter);

		assertThat(document.getFormattedContent(textFormatter, MetadataMode.ALL))
			.isEqualTo(documentWithCustomFormatter.getFormattedContent());
	}

}
