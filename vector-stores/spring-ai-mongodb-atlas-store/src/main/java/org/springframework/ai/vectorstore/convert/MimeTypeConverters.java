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
package org.springframework.ai.vectorstore.convert;

import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.util.MimeType;

import java.util.List;

import static java.util.Arrays.asList;

/**
 * Provides converters for {@link org.springframework.util.MimeType } for use with Mongo.
 *
 * @author Ross Lawley
 * @since 1.0.0
 */
public class MimeTypeConverters {

	/**
	 * @return the converters to be registered.
	 */
	public static List<Converter<?, ?>> getConvertersToRegister() {
		return asList(MimeTypeReadConverter.INSTANCE, MimeTypeWriteConverter.INSTANCE);
	}

	/**
	 * Simple singleton to convert {@link String}s to their {@link MimeType}
	 * representation.
	 */
	@ReadingConverter
	enum MimeTypeReadConverter implements Converter<String, MimeType> {

		INSTANCE;

		public MimeType convert(String source) {
			return MimeType.valueOf(source);
		}

	}

	/**
	 * Simple singleton to convert {@link MimeType}s to their {@link String}
	 * representation.
	 */
	@WritingConverter
	enum MimeTypeWriteConverter implements Converter<MimeType, String> {

		INSTANCE;

		public String convert(MimeType source) {
			return source.toString();
		}

	}

}
