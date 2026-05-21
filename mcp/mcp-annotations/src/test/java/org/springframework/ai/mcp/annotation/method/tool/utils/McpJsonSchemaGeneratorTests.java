package org.springframework.ai.mcp.annotation.method.tool.utils;

import io.swagger.v3.oas.annotations.media.Schema;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

public class McpJsonSchemaGeneratorTests {

	private final JsonMapper jsonMapper = new JsonMapper();

	@Test
	void mcpJsonSchemaGeneratorSinglePojo() throws Exception {
		String schema = McpJsonSchemaGenerator.generateForMethodInput(Executor.class.getMethod("execute", Param.class));
		JsonNode node = jsonMapper.readTree(schema);
		assert node.findPath("properties").hasNonNull("name");
	}

	@Test
	void mcpJsonSchemaGeneratorMultiPojo() throws Exception {
		String schema = McpJsonSchemaGenerator
			.generateForMethodInput(Executor.class.getMethod("execute", Param.class, Param.class));
		JsonNode node = jsonMapper.readTree(schema);
		assert node.findPath("properties").hasNonNull("param");
		assert node.findPath("properties").hasNonNull("param2");
	}

	@Test
	void mcpJsonSchemaGenerator() throws Exception {
		String schema = McpJsonSchemaGenerator.generateForMethodInput(Executor.class.getMethod("execute", int.class));
		JsonNode node = jsonMapper.readTree(schema);
		assert node.findPath("properties").hasNonNull("param");
	}

	public static class Executor {

		public void execute(Param param) {
		}

		public void execute(Param param, Param param2) {
		}

		public void execute(int param) {
		}

		public void execute(Map<?, ?> param) {
		}

		public void execute(List<?> param) {
		}

	}

	@Schema
	public static class Param {

		@Schema(name = "name", description = "参数名", requiredMode = Schema.RequiredMode.REQUIRED)
		private String name;

		@Schema(name = "type", description = "参数类型", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
		private String type;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getType() {
			return type;
		}

		public void setType(String type) {
			this.type = type;
		}

	}

}
