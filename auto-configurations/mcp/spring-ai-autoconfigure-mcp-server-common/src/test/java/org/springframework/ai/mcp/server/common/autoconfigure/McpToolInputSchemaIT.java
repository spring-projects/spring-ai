/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.mcp.server.common.autoconfigure;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.modelcontextprotocol.server.McpAsyncServer;
import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServer;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.method.tool.utils.McpJsonSchemaGenerator;
import org.springframework.ai.mcp.server.common.autoconfigure.annotations.McpServerAnnotationScannerAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.annotations.McpServerSpecificationFactoryAutoConfiguration;
import org.springframework.ai.util.json.JsonParser;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.stereotype.Component;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link McpJsonSchemaGenerator} correctly populates the {@code required}
 * array in tool input schemas, respecting {@code @Nullable} on both top-level method
 * parameters and fields of nested record types.
 */
public class McpToolInputSchemaIT {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(McpServerAutoConfiguration.class,
				McpServerJsonMapperAutoConfiguration.class, ToolCallbackConverterAutoConfiguration.class));

	/**
	 * A top-level {@code @Nullable} parameter must be absent from the {@code required}
	 * array.
	 */
	@Test
	void topLevelNullableParam_isNotRequired() throws Exception {
		Method method = JiraToolMethods.class.getDeclaredMethod("toolWithMixedParams", String.class, String.class);
		String schemaJson = McpJsonSchemaGenerator.generateForMethodInput(method);

		JsonNode schema = JsonParser.getJsonMapper().readTree(schemaJson);
		List<String> required = requiredList(schema);

		assertThat(required).as("non-nullable top-level param must be required").contains("requiredParam");
		assertThat(required).as("@Nullable top-level param must NOT be required").doesNotContain("optionalParam");
	}

	/**
	 * {@code @Nullable} on a field of a nested record must exclude that field from the
	 * nested schema's {@code required} array.
	 */
	@Test
	void nestedRecordNullableField_isNotRequired() throws Exception {
		Method method = JiraToolMethods.class.getDeclaredMethod("createIssue", JiraCreateIssueRequest.class);
		String schemaJson = McpJsonSchemaGenerator.generateForMethodInput(method);

		JsonNode schema = JsonParser.getJsonMapper().readTree(schemaJson);

		// The single method parameter "request" is not @Nullable → required at top level
		assertThat(requiredList(schema)).as("'request' param must be required at top level").contains("request");

		// Inside JiraCreateIssueRequest, "summary" carries @Nullable → NOT required
		JsonNode requestSchema = schema.at("/properties/request");
		assertThat(requestSchema.isMissingNode()).as("request schema node must exist").isFalse();
		List<String> requestRequired = requiredList(requestSchema);
		assertThat(requestRequired).as("non-nullable 'project' must be required inside request").contains("project");
		assertThat(requestRequired).as("non-nullable 'issueType' must be required inside request")
			.contains("issueType");
		assertThat(requestRequired).as("@Nullable 'summary' must NOT be required inside request")
			.doesNotContain("summary");

		// Inside Project, "description" carries @Nullable → NOT required
		JsonNode projectSchema = requestSchema.at("/properties/project");
		assertThat(projectSchema.isMissingNode()).as("project schema node must exist").isFalse();
		List<String> projectRequired = requiredList(projectSchema);
		assertThat(projectRequired).as("non-nullable Project.key must be required").contains("key");
		assertThat(projectRequired).as("non-nullable Project.name must be required").contains("name");
		assertThat(projectRequired).as("@Nullable Project.description must NOT be required")
			.doesNotContain("description");

		// Inside IssueType, "description" carries @Nullable → NOT required
		JsonNode issueTypeSchema = requestSchema.at("/properties/issueType");
		assertThat(issueTypeSchema.isMissingNode()).as("issueType schema node must exist").isFalse();
		List<String> issueTypeRequired = requiredList(issueTypeSchema);
		assertThat(issueTypeRequired).as("non-nullable IssueType.name must be required").contains("name");
		assertThat(issueTypeRequired).as("@Nullable IssueType.description must NOT be required")
			.doesNotContain("description");
	}

	/**
	 * {@code @JsonProperty(required = false)} on a nested record field must exclude it
	 * from the schema's {@code required} array.
	 */
	@Test
	void nestedRecordJsonPropertyFalse_isNotRequired() throws Exception {
		Method method = JiraToolMethods.class.getDeclaredMethod("createIssueWorkaround",
				JiraCreateIssueRequestWorkaround.class);
		String schemaJson = McpJsonSchemaGenerator.generateForMethodInput(method);

		JsonNode schema = JsonParser.getJsonMapper().readTree(schemaJson);
		JsonNode requestSchema = schema.at("/properties/request");

		List<String> projectRequired = requiredList(requestSchema.at("/properties/project"));
		assertThat(projectRequired).as("non-nullable Project.key must be required").contains("key");
		assertThat(projectRequired).as("non-nullable Project.name must be required").contains("name");
		assertThat(projectRequired).as("@JsonProperty(required=false) Project.description must NOT be required")
			.doesNotContain("description");

		List<String> requestRequired = requiredList(requestSchema);
		assertThat(requestRequired).as("@JsonProperty(required=false) summary must NOT be required")
			.doesNotContain("summary");
	}

	/**
	 * End-to-end: registers a tool with nested record parameters through the full
	 * auto-configuration stack and asserts it appears in the MCP server.
	 */
	@SuppressWarnings("unchecked")
	@Test
	void jiraToolRegisteredWithExpectedSchema() {
		this.contextRunner
			.withUserConfiguration(McpServerAnnotationScannerAutoConfiguration.class,
					McpServerSpecificationFactoryAutoConfiguration.class)
			.withBean(JiraToolComponent.class)
			.run(context -> {
				McpSyncServer syncServer = context.getBean(McpSyncServer.class);
				McpAsyncServer asyncServer = (McpAsyncServer) ReflectionTestUtils.getField(syncServer, "asyncServer");

				CopyOnWriteArrayList<AsyncToolSpecification> tools = (CopyOnWriteArrayList<AsyncToolSpecification>) ReflectionTestUtils
					.getField(asyncServer, "tools");

				assertThat(tools).hasSize(1);
				assertThat(tools.get(0).tool().name()).isEqualTo("create_jira_issue");

				System.out.println("create_jira_issue inputSchema:\n" + tools.get(0).tool().inputSchema());
			});
	}

	private static List<String> requiredList(JsonNode node) {
		List<String> result = new ArrayList<>();
		JsonNode arr = node.get("required");
		if (arr != null && arr.isArray()) {
			arr.forEach(n -> result.add(n.asString()));
		}
		return result;
	}

	/** Optional {@code description} field; {@code name} is required. */
	public record IssueType(String name, @Nullable String description) {
	}

	/** Optional {@code description} field; {@code key} and {@code name} are required. */
	public record Project(String key, String name, @Nullable String description) {
	}

	/**
	 * Models a Jira issue creation request with optional summary and nested required
	 * objects.
	 */
	public record JiraCreateIssueRequest(@Nullable String summary, Project project, IssueType issueType) {
	}

	/**
	 * Same as {@link Project} but uses {@code @JsonProperty(required = false)} instead of
	 * {@code @Nullable}.
	 */
	public record ProjectWorkaround(String key, String name, @JsonProperty(required = false) String description) {
	}

	/**
	 * Same as {@link JiraCreateIssueRequest} but uses {@code @JsonProperty} as the
	 * workaround.
	 */
	public record JiraCreateIssueRequestWorkaround(@JsonProperty(required = false) String summary,
			ProjectWorkaround project, IssueType issueType) {
	}

	static class JiraToolMethods {

		public String toolWithMixedParams(String requiredParam, @Nullable String optionalParam) {
			return requiredParam + optionalParam;
		}

		public String createIssue(JiraCreateIssueRequest request) {
			return "created";
		}

		public String createIssueWorkaround(JiraCreateIssueRequestWorkaround request) {
			return "created";
		}

	}

	@Component
	static class JiraToolComponent {

		@McpTool(name = "create_jira_issue", description = "Create a Jira issue")
		public String createJiraIssue(JiraCreateIssueRequest request) {
			return "created: " + request.summary();
		}

	}

}
