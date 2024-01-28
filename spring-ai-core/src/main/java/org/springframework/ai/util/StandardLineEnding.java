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
 * Enum representing standard line endings used across different platforms. This enum
 * provides a standardized way to reference common line separator characters such as
 * Carriage Return (CR), Line Feed (LF), and the combination of both (CRLF).
 */
public enum StandardLineEnding {

	/**
	 * Carriage Return (CR) character, commonly used as a line ending in Mac OS systems up
	 * to version 9, and in some network protocols.
	 */
	CR("\r"),

	/**
	 * Line Feed (LF) character, used as a line ending in Unix and Unix-like systems
	 * including Linux and macOS X, and in many network protocols.
	 */
	LF("\n"),

	/**
	 * Carriage Return (CR) followed by Line Feed (LF), used as a line ending in Windows
	 * operating systems, and in some network protocols.
	 */
	CRLF("\r\n");

	private final String lineSeparator;

	StandardLineEnding(String lineSeparator) {
		this.lineSeparator = lineSeparator;
	}

	/**
	 * Returns the line separator string associated with the line ending.
	 * @return The line separator string.
	 */
	public String lineSeparator() {
		return lineSeparator;
	}

	/**
	 * Returns the string representation of the line ending.This method is overridden to
	 * return the actual line separator string.
	 * @return The line separator string.
	 */
	@Override
	public String toString() {
		return lineSeparator();
	}

	/**
	 * Finds the corresponding StandardLineEnding enum constant for a given line separator
	 * string.
	 * @param lineSeparator The line separator string.
	 * @return The corresponding StandardLineEnding enum constant, or null if not found.
	 * @throws IllegalArgumentException if the line separator is not a recognized
	 * standard.
	 */
	public static StandardLineEnding findByLineSeparator(String lineSeparator) {
		for (StandardLineEnding ending : values()) {
			if (ending.lineSeparator().equals(lineSeparator)) {
				return ending;
			}
		}
		throw new IllegalArgumentException("Unrecognized line separator: '" + lineSeparator + "'");
	}

}
