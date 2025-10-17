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

package org.springframework.ai.vectorstore.filter;

/**
 * Utility class for safely escaping strings in filter expressions to prevent injection
 * attacks. This class provides methods to escape special characters that could be used to
 * break filter expression syntax or cause security vulnerabilities.
 *
 * @author Spring AI Team
 * @since 1.0.0
 */
public final class FilterStringEscapeUtils {

	private FilterStringEscapeUtils() {
		// Utility class - prevent instantiation
		throw new IllegalStateException("Utility class");
	}

	/**
	 * Escape characters for double-quoted strings (used in GraphQL, JSON, etc.). Escapes:
	 * ", \, \n, \r, \t, \b, \f
	 * @param input the string to escape
	 * @return the escaped string safe for use in double-quoted contexts
	 */
	public static String escapeForDoubleQuotes(String input) {
		if (input == null) {
			return null;
		}

		StringBuilder result = new StringBuilder();
		for (char c : input.toCharArray()) {
			switch (c) {
				case '"' -> result.append("\\\"");
				case '\\' -> result.append("\\\\");
				case '\n' -> result.append("\\n");
				case '\r' -> result.append("\\r");
				case '\t' -> result.append("\\t");
				case '\b' -> result.append("\\b");
				case '\f' -> result.append("\\f");
				default -> result.append(c);
			}
		}
		return result.toString();
	}

	/**
	 * Escape characters for single-quoted strings (used in SQL, etc.). Escapes: ', \, \n,
	 * \r, \t, \b, \f
	 * @param input the string to escape
	 * @return the escaped string safe for use in single-quoted contexts
	 */
	public static String escapeForSingleQuotes(String input) {
		if (input == null) {
			return null;
		}

		StringBuilder result = new StringBuilder();
		for (char c : input.toCharArray()) {
			switch (c) {
				case '\'' -> result.append("\\'");
				case '\\' -> result.append("\\\\");
				case '\n' -> result.append("\\n");
				case '\r' -> result.append("\\r");
				case '\t' -> result.append("\\t");
				case '\b' -> result.append("\\b");
				case '\f' -> result.append("\\f");
				default -> result.append(c);
			}
		}
		return result.toString();
	}

	/**
	 * Escape characters for SQL identifiers and values. This method handles both single
	 * quotes and backslashes for SQL contexts.
	 * @param input the string to escape
	 * @return the escaped string safe for use in SQL contexts
	 */
	public static String escapeForSql(String input) {
		if (input == null) {
			return null;
		}
		// SQL standard: escape single quotes by doubling them
		return input.replace("'", "''");
	}

	/**
	 * Escape characters for GraphQL string values. This method handles double quotes and
	 * escape sequences for GraphQL contexts.
	 * @param input the string to escape
	 * @return the escaped string safe for use in GraphQL contexts
	 */
	public static String escapeForGraphQL(String input) {
		return escapeForDoubleQuotes(input);
	}

	/**
	 * Escape characters for JSON string values. This method handles double quotes and
	 * escape sequences for JSON contexts.
	 * @param input the string to escape
	 * @return the escaped string safe for use in JSON contexts
	 */
	public static String escapeForJson(String input) {
		return escapeForDoubleQuotes(input);
	}

	/**
	 * Escape characters for Redis search queries. Redis has specific escaping rules for
	 * special characters.
	 * @param input the string to escape
	 * @return the escaped string safe for use in Redis search contexts
	 */
	public static String escapeForRedis(String input) {
		if (input == null) {
			return null;
		}

		StringBuilder result = new StringBuilder();
		for (char c : input.toCharArray()) {
			switch (c) {
				case '\\' -> result.append("\\\\");
				case '"' -> result.append("\\\"");
				case '\'' -> result.append("\\'");
				case ' ' -> result.append("\\ ");
				case '\n' -> result.append("\\n");
				case '\r' -> result.append("\\r");
				case '\t' -> result.append("\\t");
				case '\b' -> result.append("\\b");
				case '\f' -> result.append("\\f");
				default -> result.append(c);
			}
		}
		return result.toString();
	}

	/**
	 * Escape characters for Elasticsearch queries. Elasticsearch requires specific
	 * escaping for special characters in query strings.
	 * @param input the string to escape
	 * @return the escaped string safe for use in Elasticsearch query contexts
	 */
	public static String escapeForElasticsearch(String input) {
		if (input == null) {
			return null;
		}

		StringBuilder result = new StringBuilder();
		for (char c : input.toCharArray()) {
			switch (c) {
				case '\\' -> result.append("\\\\");
				case '"' -> result.append("\\\"");
				case '\'' -> result.append("\\'");
				case '+' -> result.append("\\+");
				case '-' -> result.append("\\-");
				case '=' -> result.append("\\=");
				case '&' -> result.append("\\&");
				case '|' -> result.append("\\|");
				case '!' -> result.append("\\!");
				case '(' -> result.append("\\(");
				case ')' -> result.append("\\)");
				case '{' -> result.append("\\{");
				case '}' -> result.append("\\}");
				case '[' -> result.append("\\[");
				case ']' -> result.append("\\]");
				case '^' -> result.append("\\^");
				case '~' -> result.append("\\~");
				case '*' -> result.append("\\*");
				case '?' -> result.append("\\?");
				case ':' -> result.append("\\:");
				case '/' -> result.append("\\/");
				case ' ' -> result.append("\\ ");
				case '\n' -> result.append("\\n");
				case '\r' -> result.append("\\r");
				case '\t' -> result.append("\\t");
				default -> result.append(c);
			}
		}
		return result.toString();
	}

	/**
	 * Generic escape method that takes an escape type parameter.
	 * @param input the string to escape
	 * @param escapeType the type of escaping to apply
	 * @return the escaped string
	 */
	public static String escape(String input, EscapeType escapeType) {
		if (input == null) {
			return null;
		}

		return switch (escapeType) {
			case DOUBLE_QUOTES -> escapeForDoubleQuotes(input);
			case SINGLE_QUOTES -> escapeForSingleQuotes(input);
			case SQL -> escapeForSql(input);
			case GRAPHQL -> escapeForGraphQL(input);
			case JSON -> escapeForJson(input);
			case REDIS -> escapeForRedis(input);
			case ELASTICSEARCH -> escapeForElasticsearch(input);
		};
	}

	/**
	 * Enumeration of different escape types supported by this utility.
	 */
	public enum EscapeType {

		DOUBLE_QUOTES, SINGLE_QUOTES, SQL, GRAPHQL, JSON, REDIS, ELASTICSEARCH

	}

}
