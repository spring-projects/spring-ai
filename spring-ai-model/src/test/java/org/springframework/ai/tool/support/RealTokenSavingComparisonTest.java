/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.tool.support;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolCommonDescription;
import org.springframework.ai.tool.annotation.ToolCommonDescriptions;
import org.springframework.ai.tool.annotation.ToolParam;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test to demonstrate REAL token usage savings with reference-based common descriptions.
 * This test shows how the new approach actually reduces token usage by avoiding
 * duplication.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
class RealTokenSavingComparisonTest {

	@Test
	void shouldDemonstrateRealTokenSaving() throws Exception {
		// Traditional approach - each tool has full description
		int traditionalTokens = measureTraditionalApproachTokens();

		// Reference-based approach - common descriptions are separate
		int referenceBasedTokens = measureReferenceBasedApproachTokens();

		double savingPercentage = ((double) (traditionalTokens - referenceBasedTokens) / traditionalTokens) * 100;

		System.out.println("=== Real Token Usage Comparison for Issue #4287 ===");
		System.out.println("Traditional approach tokens: " + traditionalTokens);
		System.out.println("Reference-based approach tokens: " + referenceBasedTokens);
		System.out.println("Tokens saved: " + (traditionalTokens - referenceBasedTokens));
		System.out.println("Saving percentage: " + String.format("%.1f%%", savingPercentage));
		System.out.println("===============================================");

		// Verify that reference-based approach saves significant tokens
		assertThat(referenceBasedTokens).isLessThan(traditionalTokens);
		assertThat(savingPercentage).isGreaterThan(50); // Should save at least 50%
	}

	private int measureTraditionalApproachTokens() throws Exception {
		int totalTokens = 0;

		System.out.println("\n=== Traditional Approach Tool Descriptions ===");
		for (Method method : TraditionalTimeTools.class.getMethods()) {
			if (method.isAnnotationPresent(Tool.class)) {
				String description = ToolUtils.getToolDescription(method);
				int tokens = estimateTokenCount(description);
				totalTokens += tokens;
				System.out.println("Tool: " + method.getName() + " (" + tokens + " tokens)");
				System.out.println("Description: " + description);
				System.out.println("---");
			}
		}

		return totalTokens;
	}

	private int measureReferenceBasedApproachTokens() throws Exception {
		int totalTokens = 0;

		System.out.println("\n=== Reference-Based Approach Tool Descriptions ===");

		// Add common descriptions once (not per tool)
		String commonDescs = ToolUtils.getClassCommonDescriptions(ReferenceBasedTimeTools.class);
		if (!commonDescs.isEmpty()) {
			int commonTokens = estimateTokenCount(commonDescs);
			totalTokens += commonTokens;
			System.out.println("Common Descriptions (" + commonTokens + " tokens):");
			System.out.println(commonDescs);
			System.out.println("---");
		}

		// Add individual tool descriptions (short)
		for (Method method : ReferenceBasedTimeTools.class.getMethods()) {
			if (method.isAnnotationPresent(Tool.class)) {
				String description = ToolUtils.getToolDescription(method);
				int tokens = estimateTokenCount(description);
				totalTokens += tokens;
				System.out.println("Tool: " + method.getName() + " (" + tokens + " tokens)");
				System.out.println("Description: " + description);
				System.out.println("---");
			}
		}

		return totalTokens;
	}

	/**
	 * Simple token estimation (roughly 4 characters = 1 token for English text)
	 */
	private int estimateTokenCount(String text) {
		if (text == null) {
			return 0;
		}
		return Math.max(1, text.length() / 4);
	}

	// Traditional approach - each tool repeats the full description
	static class TraditionalTimeTools {

		@Tool(description = "get current time by custom format. Supported formats are 'yyyy-MM-dd' for ISO standard, "
				+ "'dd/MM/yyyy' for European format, 'MM-dd-yyyy' for US format, 'yyyy/MM/dd' for Japanese format. "
				+ "Timezone must be a valid IANA timezone identifier such as 'UTC', 'America/New_York', "
				+ "'Europe/London', 'Asia/Tokyo', 'America/Los_Angeles'. Output will be formatted according to "
				+ "the specified pattern with proper timezone conversion.")
		public String getCurrentTime(@ToolParam(description = "date format pattern") String format,
				@ToolParam(description = "target timezone") String timezone) {
			return "current time";
		}

		@Tool(description = "format timestamp by custom format. Supported formats are 'yyyy-MM-dd' for ISO standard, "
				+ "'dd/MM/yyyy' for European format, 'MM-dd-yyyy' for US format, 'yyyy/MM/dd' for Japanese format. "
				+ "Timezone must be a valid IANA timezone identifier such as 'UTC', 'America/New_York', "
				+ "'Europe/London', 'Asia/Tokyo', 'America/Los_Angeles'. Output will be formatted according to "
				+ "the specified pattern with proper timezone conversion.")
		public String formatTimestamp(long timestamp, @ToolParam(description = "date format pattern") String format,
				@ToolParam(description = "target timezone") String timezone) {
			return "formatted timestamp";
		}

		@Tool(description = "parse datetime string with format. Supported formats are 'yyyy-MM-dd' for ISO standard, "
				+ "'dd/MM/yyyy' for European format, 'MM-dd-yyyy' for US format, 'yyyy/MM/dd' for Japanese format. "
				+ "Timezone must be a valid IANA timezone identifier such as 'UTC', 'America/New_York', "
				+ "'Europe/London', 'Asia/Tokyo', 'America/Los_Angeles'. Output will be formatted according to "
				+ "the specified pattern with proper timezone conversion.")
		public String parseDateTime(@ToolParam(description = "input datetime string") String dateTimeStr,
				@ToolParam(description = "date format pattern") String format) {
			return "parsed datetime";
		}

		@Tool(description = "convert timezone for time string. Supported formats are 'yyyy-MM-dd' for ISO standard, "
				+ "'dd/MM/yyyy' for European format, 'MM-dd-yyyy' for US format, 'yyyy/MM/dd' for Japanese format. "
				+ "Timezone must be a valid IANA timezone identifier such as 'UTC', 'America/New_York', "
				+ "'Europe/London', 'Asia/Tokyo', 'America/Los_Angeles'. Output will be formatted according to "
				+ "the specified pattern with proper timezone conversion.")
		public String convertTimezone(@ToolParam(description = "input time string") String timeStr,
				@ToolParam(description = "source timezone") String fromTz,
				@ToolParam(description = "target timezone") String toTz) {
			return "converted time";
		}

		@Tool(description = "add duration to existing time. Supported formats are 'yyyy-MM-dd' for ISO standard, "
				+ "'dd/MM/yyyy' for European format, 'MM-dd-yyyy' for US format, 'yyyy/MM/dd' for Japanese format. "
				+ "Timezone must be a valid IANA timezone identifier such as 'UTC', 'America/New_York', "
				+ "'Europe/London', 'Asia/Tokyo', 'America/Los_Angeles'. Output will be formatted according to "
				+ "the specified pattern with proper timezone conversion.")
		public String addDuration(@ToolParam(description = "base time string") String baseTime,
				@ToolParam(description = "duration to add") String duration,
				@ToolParam(description = "date format pattern") String format) {
			return "time with duration added";
		}

	}

	// Reference-based approach - common descriptions are separate
	@ToolCommonDescriptions({ @ToolCommonDescription(key = "dateTimeFormats",
			description = "Supported formats are 'yyyy-MM-dd' for ISO standard, "
					+ "'dd/MM/yyyy' for European format, 'MM-dd-yyyy' for US format, 'yyyy/MM/dd' for Japanese format. "
					+ "Timezone must be a valid IANA timezone identifier such as 'UTC', 'America/New_York', "
					+ "'Europe/London', 'Asia/Tokyo', 'America/Los_Angeles'. Output will be formatted according to "
					+ "the specified pattern with proper timezone conversion.") })
	static class ReferenceBasedTimeTools {

		@Tool(description = "get current time", commonDescriptionRef = "dateTimeFormats")
		public String getCurrentTime(@ToolParam(description = "date format pattern") String format,
				@ToolParam(description = "target timezone") String timezone) {
			return "current time";
		}

		@Tool(description = "format timestamp", commonDescriptionRef = "dateTimeFormats")
		public String formatTimestamp(long timestamp, @ToolParam(description = "date format pattern") String format,
				@ToolParam(description = "target timezone") String timezone) {
			return "formatted timestamp";
		}

		@Tool(description = "parse datetime string", commonDescriptionRef = "dateTimeFormats")
		public String parseDateTime(@ToolParam(description = "input datetime string") String dateTimeStr,
				@ToolParam(description = "date format pattern") String format) {
			return "parsed datetime";
		}

		@Tool(description = "convert timezone", commonDescriptionRef = "dateTimeFormats")
		public String convertTimezone(@ToolParam(description = "input time string") String timeStr,
				@ToolParam(description = "source timezone") String fromTz,
				@ToolParam(description = "target timezone") String toTz) {
			return "converted time";
		}

		@Tool(description = "add duration", commonDescriptionRef = "dateTimeFormats")
		public String addDuration(@ToolParam(description = "base time string") String baseTime,
				@ToolParam(description = "duration to add") String duration,
				@ToolParam(description = "date format pattern") String format) {
			return "time with duration added";
		}

	}

}
