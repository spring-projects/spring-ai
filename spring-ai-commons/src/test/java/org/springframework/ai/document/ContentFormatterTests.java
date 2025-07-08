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

package org.springframework.ai.document;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 */
class ContentFormatterTests {

	Document document = new Document("The World is Big and Salvation Lurks Around the Corner",
			Map.of("embedKey1", "value1", "embedKey2", "value2", "embedKey3", "value3", "llmKey2", "value4"));

	@Test
	void noExplicitlySetFormatter() {
		TextBlockAssertion.assertThat(this.document.getText()).isEqualTo("""
				The World is Big and Salvation Lurks Around the Corner""");

		assertThat(this.document.getFormattedContent()).isEqualTo(this.document.getFormattedContent(MetadataMode.ALL));
		assertThat(this.document.getFormattedContent())
			.isEqualTo(this.document.getFormattedContent(Document.DEFAULT_CONTENT_FORMATTER, MetadataMode.ALL));

	}

	@Test
	void defaultConfigTextFormatter() {

		DefaultContentFormatter defaultConfigFormatter = DefaultContentFormatter.defaultConfig();

		TextBlockAssertion.assertThat(this.document.getFormattedContent(defaultConfigFormatter, MetadataMode.ALL))
			.isEqualTo("""
					llmKey2: value4
					embedKey1: value1
					embedKey2: value2
					embedKey3: value3

					The World is Big and Salvation Lurks Around the Corner""");

		assertThat(this.document.getFormattedContent(defaultConfigFormatter, MetadataMode.ALL))
			.isEqualTo(this.document.getFormattedContent());

		assertThat(this.document.getFormattedContent(defaultConfigFormatter, MetadataMode.ALL))
			.isEqualTo(defaultConfigFormatter.format(this.document, MetadataMode.ALL));
	}

}
