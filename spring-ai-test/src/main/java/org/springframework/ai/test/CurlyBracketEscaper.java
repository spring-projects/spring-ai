/*
* Copyright 2025 - 2025 the original author or authors.
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
package org.springframework.ai.test;

/**
 * Utility class for escaping curly brackets in strings
 *
 * @author Christian Tzolov
 *
 */
public class CurlyBracketEscaper {

	/**
	 * Escapes all curly brackets in the input string by adding a backslash before them
	 * @param input The string containing curly brackets to escape
	 * @return The string with escaped curly brackets
	 */
	public static String escapeCurlyBrackets(String input) {
		if (input == null) {
			return null;
		}
		return input.replace("{", "\\{").replace("}", "\\}");
	}

	/**
	 * Unescapes previously escaped curly brackets by removing the backslashes
	 * @param input The string containing escaped curly brackets
	 * @return The string with unescaped curly brackets
	 */
	public static String unescapeCurlyBrackets(String input) {
		if (input == null) {
			return null;
		}
		return input.replace("\\{", "{").replace("\\}", "}");
	}

}