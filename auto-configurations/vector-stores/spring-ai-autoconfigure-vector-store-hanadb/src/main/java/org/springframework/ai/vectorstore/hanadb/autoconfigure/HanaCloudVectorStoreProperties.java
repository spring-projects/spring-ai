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

package org.springframework.ai.vectorstore.hanadb.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Hana Cloud Vector Store.
 *
 * @author Rahul Mittal
 * @since 1.0.0
 */
@ConfigurationProperties(HanaCloudVectorStoreProperties.CONFIG_PREFIX)
public class HanaCloudVectorStoreProperties {

	public static final String CONFIG_PREFIX = "spring.ai.vectorstore.hanadb";

	private String tableName;

	private int topK;

	public String getTableName() {
		return this.tableName;
	}

	public void setTableName(String tableName) {
		this.tableName = tableName;
	}

	public int getTopK() {
		return this.topK;
	}

	public void setTopK(int topK) {
		this.topK = topK;
	}

}
