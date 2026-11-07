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

package org.springframework.ai.converter;

import java.util.Collections;
import java.util.List;

import org.jspecify.annotations.Nullable;

import org.springframework.core.convert.support.DefaultConversionService;

/**
 * {@link StructuredOutputConverter} implementation that uses a
 * {@link DefaultConversionService} to convert the LLM output into a
 * {@link java.util.List} instance.
 *
 * @author Mark Pollack
 * @author Christian Tzolov
 */
public class ListOutputConverter extends AbstractConversionServiceOutputConverter<List<String>> {

	private final ResponseTextCleaner textCleaner;

	public ListOutputConverter() {
		this(new DefaultConversionService());
	}

	public ListOutputConverter(DefaultConversionService defaultConversionService) {
		this(defaultConversionService, null);
	}

	/**
	 * @param textCleaner cleaner applied to the response before parsing, or {@code null}
	 * to use {@link ResponseTextCleaner#defaultCleaner()}
	 * @since 1.1.0
	 */
	public ListOutputConverter(DefaultConversionService defaultConversionService,
			@Nullable ResponseTextCleaner textCleaner) {
		super(defaultConversionService);
		this.textCleaner = (textCleaner != null) ? textCleaner : ResponseTextCleaner.defaultCleaner();
	}

	@Override
	public String getFormat() {
		return """
				Respond with only a list of comma-separated values, without any leading or trailing text.
				Example format: foo, bar, baz
				""";
	}

	@Override
	public List<String> convert(String text) {
		String cleaned = this.textCleaner.clean(text);
		// an all-whitespace response cleans down to nothing; keep the original so the
		// conversion service decides what that means
		if (cleaned != null && !cleaned.isEmpty()) {
			text = cleaned;
		}
		List<String> result = this.getConversionService().convert(text, List.class);
		return result == null ? Collections.emptyList() : result;
	}

}
