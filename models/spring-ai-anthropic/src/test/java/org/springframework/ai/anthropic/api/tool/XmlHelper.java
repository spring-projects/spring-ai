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
package org.springframework.ai.anthropic.api.tool;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import org.springframework.util.StringUtils;

/**
 * @author Christian Tzolov
 */
public class XmlHelper {

	// Regular expression to match XML block between <function_calls> and
	// </function_calls> tags
	private static final String FUNCTION_CALLS_REGEX = "<function_calls>.*?</function_calls>";

	// Compile the regular expression pattern
	private static final Pattern FUNCTION_CALLS_PATTERN = Pattern.compile(FUNCTION_CALLS_REGEX, Pattern.DOTALL);

	private static final XmlMapper xmlMapper = new XmlMapper();

	@JsonInclude(Include.NON_NULL) // @formatter:off
	@JacksonXmlRootElement(localName = "tools")
	public record Tools(
		@JacksonXmlElementWrapper(useWrapping = false) @JsonProperty("tool_description") List<ToolDescription> toolDescriptions) {

		public record ToolDescription(
			@JsonProperty("tool_name") String toolName,
			@JsonProperty("description") String description,
			@JacksonXmlElementWrapper(localName = "parameters") @JsonProperty("parameter") List<Parameter> parameters) {

			@JacksonXmlRootElement(localName = "parameter")
			public record Parameter(
					@JsonProperty("name") String name,
					@JsonProperty("type") String type,
					@JsonProperty("description") String description) {
			}
		}
 	} // @formatter:on

	@JsonInclude(Include.NON_NULL) // @formatter:off
	@JacksonXmlRootElement(localName = "function_calls")
	public record FunctionCalls(@JsonProperty("invoke") Invoke invoke) {
		public record Invoke(
				@JsonProperty("tool_name") String toolName,
				@JsonProperty("parameters") Map<String, Object> parameters) {
		}
	} // @formatter:on

	@JsonInclude(Include.NON_NULL) // @formatter:off
	@JacksonXmlRootElement(localName = "function_results")
	public record FunctionResults(
		@JacksonXmlElementWrapper(useWrapping = false) @JsonProperty("result") List<Result> result) {

		public record Result(
				@JsonProperty("tool_name") String toolName,
				@JsonProperty("stdout") Object stdout) {
		}
	} // @formatter:on

	public static String extractFunctionCallsXmlBlock(String text) {
		if (!StringUtils.hasText(text)) {
			return "";
		}

		Matcher matcher = FUNCTION_CALLS_PATTERN.matcher(text);

		// Find and print the XML block
		return (matcher.find()) ? matcher.group() : "";
	}

	public static FunctionCalls extractFunctionCalls(String text) {

		String xml = extractFunctionCallsXmlBlock(text);

		if (!StringUtils.hasText(xml)) {
			return null;
		}

		try {
			FunctionCalls functionCalls = xmlMapper.readValue(xml, FunctionCalls.class);
			return functionCalls;
		}
		catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	public static String toXml(Object object) {
		try {
			return xmlMapper.writerWithDefaultPrettyPrinter().writeValueAsString(object);
		}
		catch (JsonProcessingException e) {
			e.printStackTrace();
			return "";
		}
	}

	public static void main(String[] args) throws JsonMappingException, JsonProcessingException {

		String sample = """
				<function_calls>
					<invoke>
						<tool_name>getCurrentWeather</tool_name>
						<parameters>
							<location>San Francisco, CA</location>
							<unit>Celsius</unit>
						</parameters>
					</invoke>
				</function_calls>
				""";

		System.out.println(extractFunctionCalls(sample));

		var toolDescription = new Tools.ToolDescription("getCurrentWeather",
				"Get the weather in location. Return temperature in 30°F or 30°C format.",
				List.of(new Tools.ToolDescription.Parameter("location", "string",
						"The city and state e.g. San Francisco, CA"),
						new Tools.ToolDescription.Parameter("unit", "enum", "Temperature unit")));

		System.out.println(toXml(new Tools(List.of(toolDescription))));

	}

}
