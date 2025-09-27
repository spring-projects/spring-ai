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

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolCommonDescription;
import org.springframework.ai.tool.annotation.ToolCommonDescriptions;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * Example demonstrating the reference-based common description approach. This shows how
 * multiple tools can reference the same detailed description without duplication, helping
 * to reduce input length when sending tool definitions to language models.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
@ToolCommonDescriptions({ @ToolCommonDescription(key = "dateTimeFormats",
		description = "Supported formats are 'yyyy-MM-dd' for ISO standard, "
				+ "'dd/MM/yyyy' for European format, 'MM-dd-yyyy' for US format, 'yyyy/MM/dd' for Japanese format. "
				+ "Timezone must be a valid IANA timezone identifier such as 'UTC', 'America/New_York', "
				+ "'Europe/London', 'Asia/Tokyo', 'America/Los_Angeles'. Output will be formatted according to "
				+ "the specified pattern with proper timezone conversion."),
		@ToolCommonDescription(key = "validationRules",
				description = "Input validation: All parameters must be non-null, non-empty strings. "
						+ "Format strings must follow the specified patterns. Timezone identifiers must be valid IANA timezones.") })
public class ReferenceBasedCommonDescriptionExample {

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

	@Tool(description = "validate input", commonDescriptionRef = "validationRules")
	public String validateInput(@ToolParam(description = "input to validate") String input) {
		return "validation result";
	}

	@Tool(description = "simple tool without common description")
	public String simpleTool(@ToolParam(description = "input parameter") String input) {
		return "simple result";
	}

}
