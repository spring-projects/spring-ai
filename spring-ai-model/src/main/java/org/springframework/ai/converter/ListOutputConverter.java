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

package org.springframework.ai.converter;

import java.util.Collections;
import java.util.List;

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

	public ListOutputConverter() {
		this(new DefaultConversionService());
	}

	public ListOutputConverter(DefaultConversionService defaultConversionService) {
		super(defaultConversionService);
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
		List<String> result = this.getConversionService().convert(text, List.class);
		return result == null ? Collections.emptyList() : result;
	}

}
