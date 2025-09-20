/*
 * Copyright 2023-2025 the original author or authors.
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
import org.springframework.ai.tool.annotation.ToolClassCommonDescription;
import org.springframework.ai.tool.annotation.ToolParam;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test to demonstrate token usage savings with shared descriptions. This test validates
 * the solution for issue #4287 by measuring actual token usage.
 */
class TokenUsageComparisonTest {

	@Test
	void shouldReduceTokenUsageSignificantly() throws Exception {
		// Test traditional approach vs shared description approach
		int traditionalTokens = measureTraditionalApproachTokens();
		int sharedDescriptionTokens = measureSharedDescriptionApproachTokens();

		System.out.println("=== Token Usage Comparison for Issue #4287 ===");
		System.out.println("Traditional approach tokens: " + traditionalTokens);
		System.out.println("Shared description approach tokens: " + sharedDescriptionTokens);

		int savedTokens = traditionalTokens - sharedDescriptionTokens;
		double savingPercentage = ((double) savedTokens / traditionalTokens) * 100;

		System.out.println("Tokens saved: " + savedTokens);
		System.out.println("Saving percentage: " + String.format("%.1f%%", savingPercentage));
		System.out.println("===============================================");

		// Verify that shared descriptions save tokens
		assertThat(sharedDescriptionTokens).isLessThan(traditionalTokens);
		assertThat(savingPercentage).isGreaterThan(30); // Should save at least 30%
	}

	private int measureTraditionalApproachTokens() throws Exception {
		// Simulate traditional approach where each tool repeats the same description
		int totalTokens = 0;

		for (Method method : TraditionalTimeTools.class.getMethods()) {
			if (method.isAnnotationPresent(Tool.class)) {
				String description = ToolUtils.getToolDescription(method);
				totalTokens += estimateTokenCount(description);
			}
		}

		return totalTokens;
	}

	private int measureSharedDescriptionApproachTokens() throws Exception {
		// Measure class-level common description approach
		int totalTokens = 0;

		System.out.println("\n=== Class-Level Common Description Approach Tool Descriptions ===");
		for (Method method : ClassLevelCommonDescriptionTimeTools.class.getMethods()) {
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
	 * Simple token estimation (roughly 4 characters = 1 token for English text) This is a
	 * simplified estimation for demonstration purposes.
	 */
	private int estimateTokenCount(String text) {
		if (text == null) {
			return 0;
		}
		// Rough estimation: 1 token ≈ 4 characters for English
		// This is simplified but good enough for comparison
		return (text.length() + 3) / 4; // +3 for rounding up
	}

	// Traditional approach - lots of repetition
	static class TraditionalTimeTools {

		@Tool(description = "Get current time by custom format. Supported date formats are 'yyyy-MM-dd' for ISO standard, 'dd/MM/yyyy' for European format, 'MM-dd-yyyy' for US format, 'yyyy/MM/dd' for Japanese format. Timezone must be a valid IANA timezone identifier such as 'UTC', 'America/New_York', 'Europe/London', 'Asia/Tokyo', 'America/Los_Angeles'. Output will be formatted according to the specified pattern with proper timezone conversion.")
		public String getCurrentTime(@ToolParam(description = "date format pattern") String format,
				@ToolParam(description = "target timezone") String timezone) {
			return "current time";
		}

		@Tool(description = "Format existing timestamp by custom format. Supported date formats are 'yyyy-MM-dd' for ISO standard, 'dd/MM/yyyy' for European format, 'MM-dd-yyyy' for US format, 'yyyy/MM/dd' for Japanese format. Timezone must be a valid IANA timezone identifier such as 'UTC', 'America/New_York', 'Europe/London', 'Asia/Tokyo', 'America/Los_Angeles'. Output will be formatted according to the specified pattern with proper timezone conversion.")
		public String formatTimestamp(long timestamp, @ToolParam(description = "date format pattern") String format,
				@ToolParam(description = "target timezone") String timezone) {
			return "formatted timestamp";
		}

		@Tool(description = "Convert time between different timezones. Supported date formats are 'yyyy-MM-dd' for ISO standard, 'dd/MM/yyyy' for European format, 'MM-dd-yyyy' for US format, 'yyyy/MM/dd' for Japanese format. Timezone must be a valid IANA timezone identifier such as 'UTC', 'America/New_York', 'Europe/London', 'Asia/Tokyo', 'America/Los_Angeles'. Output will be formatted according to the specified pattern with proper timezone conversion.")
		public String convertTimezone(@ToolParam(description = "input time string") String timeStr,
				@ToolParam(description = "source timezone") String fromTz,
				@ToolParam(description = "target timezone") String toTz) {
			return "converted time";
		}

		@Tool(description = "Parse datetime string with format. Supported date formats are 'yyyy-MM-dd' for ISO standard, 'dd/MM/yyyy' for European format, 'MM-dd-yyyy' for US format, 'yyyy/MM/dd' for Japanese format. Timezone must be a valid IANA timezone identifier such as 'UTC', 'America/New_York', 'Europe/London', 'Asia/Tokyo', 'America/Los_Angeles'. Output will be formatted according to the specified pattern with proper timezone conversion.")
		public String parseDateTime(@ToolParam(description = "input datetime string") String dateTimeStr,
				@ToolParam(description = "date format pattern") String format) {
			return "parsed datetime";
		}

		@Tool(description = "Add duration to existing time. Supported date formats are 'yyyy-MM-dd' for ISO standard, 'dd/MM/yyyy' for European format, 'MM-dd-yyyy' for US format, 'yyyy/MM/dd' for Japanese format. Timezone must be a valid IANA timezone identifier such as 'UTC', 'America/New_York', 'Europe/London', 'Asia/Tokyo', 'America/Los_Angeles'. Output will be formatted according to the specified pattern with proper timezone conversion.")
		public String addDuration(@ToolParam(description = "base time string") String baseTime,
				@ToolParam(description = "duration to add") String duration,
				@ToolParam(description = "date format pattern") String format) {
			return "time with duration added";
		}

	}

	// Class-level common description approach - Daniel's preferred solution
	@ToolClassCommonDescription("Supported date formats are 'yyyy-MM-dd' for ISO standard, "
			+ "'dd/MM/yyyy' for European format, 'MM-dd-yyyy' for US format, 'yyyy/MM/dd' for Japanese format. "
			+ "Timezone must be a valid IANA timezone identifier such as 'UTC', 'America/New_York', "
			+ "'Europe/London', 'Asia/Tokyo', 'America/Los_Angeles'. Output will be formatted according to "
			+ "the specified pattern with proper timezone conversion.")
	static class ClassLevelCommonDescriptionTimeTools {

		@Tool(description = "Get current time")
		public String getCurrentTime(@ToolParam(description = "date format pattern") String format,
				@ToolParam(description = "target timezone") String timezone) {
			return "current time";
		}

		@Tool(description = "Format timestamp")
		public String formatTimestamp(long timestamp, @ToolParam(description = "date format pattern") String format,
				@ToolParam(description = "target timezone") String timezone) {
			return "formatted timestamp";
		}

		@Tool(description = "Convert timezone")
		public String convertTimezone(@ToolParam(description = "input time string") String timeStr,
				@ToolParam(description = "source timezone") String fromTz,
				@ToolParam(description = "target timezone") String toTz) {
			return "converted time";
		}

		@Tool(description = "Parse datetime")
		public String parseDateTime(@ToolParam(description = "input datetime string") String dateTimeStr,
				@ToolParam(description = "date format pattern") String format) {
			return "parsed datetime";
		}

		@Tool(description = "Add duration")
		public String addDuration(@ToolParam(description = "base time string") String baseTime,
				@ToolParam(description = "duration to add") String duration,
				@ToolParam(description = "date format pattern") String format) {
			return "time with duration added";
		}

	}

}
