/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.google.genai.metadata;

import java.util.Locale;

import com.google.genai.types.MediaModality;
import com.google.genai.types.ModalityTokenCount;
import com.google.genai.types.TrafficType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression tests verifying that protocol/enum string casing is locale-independent.
 *
 * <p>
 * On a JVM running with the Turkish locale ({@code tr_TR}), the default
 * {@link String#toUpperCase()} / {@link String#toLowerCase()} apply Turkish-specific
 * casing rules that convert {@code "I"} to a dotless {@code "ı"} (U+0131) and lowercase
 * {@code "i"} to a dotted capital {@code "İ"}. When such conversions operate on protocol
 * tokens or enum names, the resulting values are rejected by the provider. See related
 * issues #6478, #6476 and #5340 for the same bug class in other modules.
 *
 * @author Edy Yang
 * @since 2.0.1
 */
public class GoogleGenAiMetadataLocaleTests {

	private Locale defaultLocale;

	@BeforeEach
	void setTurkishLocale() {
		this.defaultLocale = Locale.getDefault();
		Locale.setDefault(Locale.forLanguageTag("tr-TR"));
	}

	@AfterEach
	void restoreDefaultLocale() {
		Locale.setDefault(this.defaultLocale);
	}

	@Test
	void modalityTokenCountIsLocaleIndependent() {
		ModalityTokenCount textModality = ModalityTokenCount.builder()
			.modality(new MediaModality(MediaModality.Known.TEXT))
			.tokenCount(100)
			.build();

		GoogleGenAiModalityTokenCount tokenCount = GoogleGenAiModalityTokenCount.from(textModality);

		assertThat(tokenCount.getModality()).isEqualTo("TEXT");
	}

	@Test
	void trafficTypeIsLocaleIndependent() {
		GoogleGenAiTrafficType onDemand = GoogleGenAiTrafficType.from(new TrafficType(TrafficType.Known.ON_DEMAND));
		GoogleGenAiTrafficType provisioned = GoogleGenAiTrafficType
			.from(new TrafficType(TrafficType.Known.PROVISIONED_THROUGHPUT));

		assertThat(onDemand).isEqualTo(GoogleGenAiTrafficType.ON_DEMAND);
		assertThat(provisioned).isEqualTo(GoogleGenAiTrafficType.PROVISIONED_THROUGHPUT);
	}

}
