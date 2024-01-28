/*
 * Copyright 2023 the original author or authors.
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
package org.springframework.ai.util;

/**
 * A utility class for normalizing line endings in strings. This class is specifically
 * designed to handle differences in line ending characters across different operating
 * systems. It primarily addresses the discrepancy between Unix-based systems (which use
 * '\n') and Windows (which uses '\r\n').
 *
 * <p>
 * The normalization process replaces non-Unix style line endings ('\r\n') with Unix-style
 * line endings ('\n'). On systems with Unix-style line endings, this utility performs no
 * operation, effectively acting as a no-op. This design ensures minimal performance
 * overhead on systems where line ending normalization is not required and may be entirely
 * optimized away by the JIT.
 * </p>
 *
 * <p>
 * This utility can be particularly useful in scenarios where consistent line ending
 * characters are necessary, such as in text processing, file I/O operations, or when
 * dealing with multi-platform string manipulations.
 * </p>
 *
 * Usage example: <pre>
 *     LineEndingsNormalizer normalizer = new LineEndingsNormalizer();
 *     String normalizedText = normalizer.normalize(inputText);
 * </pre>
 *
 * @author Kirk Lund
 */
public class LineEndingsNormalizer {

	/**
	 * The line ending separator to normalize.
	 */
	private final String normalizedLineSeparator;

	/**
	 * Indicates if normalization of line endings is required.
	 */
	private final boolean requiresNormalization;

	/**
	 * Constructs a new LineEndingsNormalizer instance. It determines at instantiation if
	 * normalization is necessary.
	 */
	public LineEndingsNormalizer(StandardLineEnding normalizedLineEnding) {
		this(normalizedLineEnding, requiresNormalization(normalizedLineEnding));
	}

	/**
	 * Package-private constructor primarily for testing purposes.
	 * @param normalizedLineEnding Specifies the line ending to normalize to.
	 * @param requiresNormalization Specifies if normalization of line endings is
	 * required.
	 */
	LineEndingsNormalizer(StandardLineEnding normalizedLineEnding, boolean requiresNormalization) {
		this.normalizedLineSeparator = normalizedLineEnding.lineSeparator();
		this.requiresNormalization = requiresNormalization;
	}

	/**
	 * Normalizes line endings in the provided string. If the platform has non-Unix line,
	 * this method replaces all platform line endings (System.lineSeparator()) with
	 * Unix-style line endings ('\n'). On other operating systems, the input string is
	 * returned unmodified.
	 * @param input The string in which line endings are to be normalized.
	 * @return The string with normalized line endings.
	 */
	public String normalize(String input) {
		if (requiresNormalization) {
			return input.replace(System.lineSeparator(), normalizedLineSeparator);
		}
		return input;
	}

	/**
	 * Determines if the specified normalizedLineEnding requires normalization when
	 * running on the current platform.
	 * @param normalizedLineEnding Specifies the line ending to normalize to.
	 * @return true if the specified normalizedLineEnding requires normalization.
	 */
	public static boolean requiresNormalization(StandardLineEnding normalizedLineEnding) {
		StandardLineEnding currentLineEnding = StandardLineEnding.findByLineSeparator(System.lineSeparator());
		return requiresNormalization(currentLineEnding, normalizedLineEnding);
	}

	static boolean requiresNormalization(StandardLineEnding currentLineEnding,
			StandardLineEnding normalizedLineEnding) {
		return !currentLineEnding.lineSeparator().equals(normalizedLineEnding.lineSeparator());
	}

}
