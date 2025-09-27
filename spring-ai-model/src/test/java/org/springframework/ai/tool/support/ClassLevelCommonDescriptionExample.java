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
import org.springframework.ai.tool.annotation.ToolClassCommonDescription;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * Example demonstrating the class-level common description approach. This shows how
 * multiple tools can share the same detailed description without repetition, helping to
 * reduce input length when sending tool definitions to language models.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
@ToolClassCommonDescription("Supported formats are 'yyyy-MM-dd' for ISO standard, "
		+ "'dd/MM/yyyy' for European format, 'MM-dd-yyyy' for US format, 'yyyy/MM/dd' for Japanese format. "
		+ "Timezone must be a valid IANA timezone identifier such as 'UTC', 'America/New_York', "
		+ "'Europe/London', 'Asia/Tokyo', 'America/Los_Angeles'. Output will be formatted according to "
		+ "the specified pattern with proper timezone conversion.")
public class ClassLevelCommonDescriptionExample {

	@Tool(description = "get current time")
	public String getCurrentTime(@ToolParam(description = "date format pattern") String format,
			@ToolParam(description = "target timezone") String timezone) {
		return "current time";
	}

	@Tool(description = "format timestamp")
	public String formatTimestamp(long timestamp, @ToolParam(description = "date format pattern") String format,
			@ToolParam(description = "target timezone") String timezone) {
		return "formatted timestamp";
	}

	@Tool(description = "parse datetime string")
	public String parseDateTime(@ToolParam(description = "input datetime string") String dateTimeStr,
			@ToolParam(description = "date format pattern") String format) {
		return "parsed datetime";
	}

	@Tool(description = "convert timezone")
	public String convertTimezone(@ToolParam(description = "input time string") String timeStr,
			@ToolParam(description = "source timezone") String fromTz,
			@ToolParam(description = "target timezone") String toTz) {
		return "converted time";
	}

}
