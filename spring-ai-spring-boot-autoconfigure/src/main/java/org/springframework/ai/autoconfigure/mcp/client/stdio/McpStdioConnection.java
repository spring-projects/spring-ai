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

package org.springframework.ai.autoconfigure.mcp.client.stdio;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.client.transport.ServerParameters;

public class McpStdioConnection {

	private String command;

	private List<String> args = new ArrayList<>();

	private Map<String, String> env;

	public String getCommand() {
		return this.command;
	}

	public void setCommand(String command) {
		this.command = command;
	}

	public List<String> getArgs() {
		return this.args;
	}

	public void setArgs(List<String> args) {
		this.args = args;
	}

	public Map<String, String> getEnv() {
		return this.env;
	}

	public void setEnv(Map<String, String> env) {
		this.env = env;
	}

	public ServerParameters toServerParameters() {
		return ServerParameters.builder(this.command).args(this.args).env(this.env).build();
	}

}
