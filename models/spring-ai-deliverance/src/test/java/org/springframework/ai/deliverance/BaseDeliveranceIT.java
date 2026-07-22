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

package org.springframework.ai.deliverance;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

import org.junit.jupiter.api.Assumptions;

import org.springframework.ai.deliverance.api.DeliveranceApi;

abstract class BaseDeliveranceIT {

	private static final String DEFAULT_BASE_URL = "http://localhost:9997";

	protected static final String DEFAULT_MODEL = "Qwen3-4B-JQ4";

	protected static DeliveranceApi initializeDeliverance() {
		return DeliveranceApi.create(baseUrl(), System.getenv("DELIVERANCE_API_KEY"));
	}

	protected static String model() {
		return System.getenv().getOrDefault("DELIVERANCE_MODEL", DEFAULT_MODEL);
	}

	protected static void assumeDeliverancePortOpen() {
		Assumptions.assumeTrue(isPortOpen("localhost", port()),
				"Deliverance server is not listening on localhost:" + port());
	}

	private static String baseUrl() {
		return System.getenv().getOrDefault("DELIVERANCE_BASE_URL", DEFAULT_BASE_URL);
	}

	private static int port() {
		String baseUrl = baseUrl();
		int lastColon = baseUrl.lastIndexOf(':');
		if (lastColon == -1) {
			return 80;
		}
		int slashAfterPort = baseUrl.indexOf('/', lastColon + 1);
		String port = slashAfterPort == -1 ? baseUrl.substring(lastColon + 1)
				: baseUrl.substring(lastColon + 1, slashAfterPort);
		return Integer.parseInt(port);
	}

	private static boolean isPortOpen(String host, int port) {
		try (Socket socket = new Socket()) {
			socket.connect(new InetSocketAddress(host, port), 1000);
			return true;
		}
		catch (IOException ex) {
			return false;
		}
	}

}
