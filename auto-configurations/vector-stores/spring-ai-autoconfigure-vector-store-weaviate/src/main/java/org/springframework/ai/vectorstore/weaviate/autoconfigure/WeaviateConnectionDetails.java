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

package org.springframework.ai.vectorstore.weaviate.autoconfigure;

import org.springframework.boot.autoconfigure.service.connection.ConnectionDetails;

/**
 * Connection details for a Weaviate instance.
 *
 * <p>
 * The Weaviate {@code client6} driver connects over both HTTP (REST) and gRPC, so this
 * SPI now exposes a gRPC endpoint in addition to the HTTP one. {@link #getGrpcHost()} has
 * a default implementation deriving the gRPC endpoint from {@link #getHost()} using the
 * standard gRPC port {@code 50051}; implementations that map gRPC to a different host or
 * port (for example randomly mapped container ports) should override it.
 */
public interface WeaviateConnectionDetails extends ConnectionDetails {

	/**
	 * The default Weaviate gRPC port.
	 */
	int DEFAULT_GRPC_PORT = 50051;

	/**
	 * The HTTP (REST) endpoint, in {@code host:port} form (for example
	 * {@code localhost:8080}).
	 * @return the HTTP endpoint
	 */
	String getHost();

	/**
	 * The gRPC endpoint, in {@code host:port} form (for example {@code localhost:50051}).
	 * The default implementation reuses the host of {@link #getHost()} with the standard
	 * gRPC port {@value #DEFAULT_GRPC_PORT}.
	 * @return the gRPC endpoint
	 * @since 2.1.0
	 */
	default String getGrpcHost() {
		String http = getHost();
		int separator = (http != null) ? http.lastIndexOf(':') : -1;
		String host = (separator > -1) ? http.substring(0, separator) : http;
		return host + ":" + DEFAULT_GRPC_PORT;
	}

}
