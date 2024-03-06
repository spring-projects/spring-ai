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
package org.springframework.ai.openai.chat.api;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.Builder;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 */
public class RestClientBuilderTests {

	public static final String BASE_URL = "https://dog.ceo";

	@Test
	public void test1() {
		test(RestClient.builder(), BASE_URL);
	}

	@Test
	public void test2() {
		RestTemplate restTemplate = new RestTemplate();
		test(RestClient.builder(restTemplate), BASE_URL);
	}

	@Test
	public void test3() {
		RestTemplate restTemplate = new RestTemplate();
		restTemplate.setUriTemplateHandler(new DefaultUriBuilderFactory(BASE_URL));
		test(RestClient.builder(restTemplate), BASE_URL);
	}

	@Test
	public void test4() {
		var clientHttpRequestFactory = new SimpleClientHttpRequestFactory();
		clientHttpRequestFactory.setConnectTimeout(5000);
		// clientHttpRequestFactory.setProxy(new Proxy(Type.HTTP,
		// InetSocketAddress.createUnresolved("localhost", 80)));
		RestClient.Builder builder = RestClient.builder().requestFactory(clientHttpRequestFactory);
		test(builder, BASE_URL);
	}

	private void test(Builder restClientBuilder, String baseUrl) {
		var restClient = restClientBuilder.baseUrl(baseUrl).build();
		String res = restClient.get().uri("/api/breeds/list/all").retrieve().body(String.class);

		assertThat(res).isNotNull();
	}

}
