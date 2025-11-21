/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.testcontainers.service.connection.mongo;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

import com.mongodb.ConnectionString;
import org.testcontainers.mongodb.MongoDBAtlasLocalContainer;

import org.springframework.boot.mongodb.autoconfigure.MongoConnectionDetails;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.testcontainers.service.connection.ContainerConnectionDetailsFactory;
import org.springframework.boot.testcontainers.service.connection.ContainerConnectionSource;
import org.springframework.util.ReflectionUtils;

/**
 * A {@link ContainerConnectionDetailsFactory} implementation that provides
 * {@link MongoConnectionDetails} for a {@link MongoDBAtlasLocalContainer}.
 * <p>
 * This factory is used in conjunction with Spring Boot's auto-configuration for
 * Testcontainers to automatically create and configure a connection to a MongoDB instance
 * running in a testcontainer.
 * <p>
 * It generates {@link MongoConnectionDetails} based on the connection information
 * provided by the {@link MongoDBAtlasLocalContainer}, allowing integration of MongoDB
 * testcontainers in Spring Boot applications.
 *
 * @author Eddú Meléndez
 * @author Soby Chacko
 * @author Yanming Zhou
 * @since 1.0.0
 * @see ContainerConnectionDetailsFactory
 * @see MongoConnectionDetails
 * @see MongoDBAtlasLocalContainer
 */
class MongoDbAtlasLocalContainerConnectionDetailsFactory
		extends ContainerConnectionDetailsFactory<MongoDBAtlasLocalContainer, MongoConnectionDetails> {

	private static final Method GET_SSL_BUNDLE_METHOD;

	static {
		GET_SSL_BUNDLE_METHOD = ReflectionUtils.findMethod(MongoConnectionDetails.class, "getSslBundle");
	}

	@Override
	protected MongoConnectionDetails getContainerConnectionDetails(
			ContainerConnectionSource<MongoDBAtlasLocalContainer> source) {
		return new MongoDbAtlasLocalContainerConnectionDetails(source);
	}

	/**
	 * {@link MongoConnectionDetails} backed by a {@link ContainerConnectionSource}.
	 */
	private static final class MongoDbAtlasLocalContainerConnectionDetails
			extends ContainerConnectionDetails<MongoDBAtlasLocalContainer> implements MongoConnectionDetails {

		private MongoDbAtlasLocalContainerConnectionDetails(
				ContainerConnectionSource<MongoDBAtlasLocalContainer> source) {
			super(source);
		}

		@Override
		public ConnectionString getConnectionString() {
			return new ConnectionString(getContainer().getConnectionString());
		}

		// Conditional implementation based on whether the method exists
		public SslBundle getSslBundle() {
			if (GET_SSL_BUNDLE_METHOD != null) { // Boot 3.5.x+
				try {
					MethodHandles.Lookup origin = MethodHandles.lookup().in(getClass());
					return (SslBundle) MethodHandles.privateLookupIn(GET_SSL_BUNDLE_METHOD.getDeclaringClass(), origin)
						.unreflectSpecial(GET_SSL_BUNDLE_METHOD, GET_SSL_BUNDLE_METHOD.getDeclaringClass())
						.bindTo(this)
						.invokeWithArguments();
				}
				catch (Throwable e) {
					throw new RuntimeException(e);
				}
			}
			return null; // Boot 3.4.x (No-Op)
		}

	}

}
