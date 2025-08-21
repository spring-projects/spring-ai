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

package org.springframework.ai.docker.compose.service.connection.elasticsearch;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchConnectionDetails;
import org.springframework.boot.docker.compose.service.connection.test.AbstractDockerComposeIT;
import org.springframework.util.StreamUtils;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;

import static org.assertj.core.api.Assertions.assertThat;

class ElasticsearchDockerComposeConnectionDetailsFactoryIT extends AbstractDockerComposeIT {

	protected ElasticsearchDockerComposeConnectionDetailsFactoryIT() {
		super("elasticsearch-compose.yaml", DockerImageName.parse(getLatestElasticsearch()));
	}

	private static String getLatestElasticsearch(){
		String imageName = "docker.elastic.co/elasticsearch/elasticsearch:";
		try {
			URL url = new URL("https://artifacts.elastic.co/releases/stack.json");
			URLConnection connection = url.openConnection();
			try (InputStream is = connection.getInputStream()) {
				String result = StreamUtils.copyToString(is, Charset.defaultCharset());
				JSONObject json = new JSONObject(result);
				JSONArray releases = json.getJSONArray("releases");
				JSONObject latest = (JSONObject) releases.get(releases.length() - 1);
				return imageName + latest.getString("version");
			}
		}
		catch (IOException e) {
			throw new RuntimeException("Failed to retrieve latest Elasticsearch version from https://artifacts.elastic.co/releases/stack.json", e);
		}
	}

	@Test
	void runCreatesConnectionDetails() {
		ElasticsearchConnectionDetails connectionDetails = run(ElasticsearchConnectionDetails.class);
		assertThat(connectionDetails.getNodes()).isNotEmpty();
	}

}
