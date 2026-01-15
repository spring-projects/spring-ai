/*
 * Copyright 2025-2026 the original author or authors.
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

package org.springframework.ai.vectorstore.couchbase;

import org.testcontainers.couchbase.BucketDefinition;
import org.testcontainers.utility.DockerImageName;

/**
 * @author Laurent Doguin
 * @since 1.0.0
 */
public final class CouchbaseContainerMetadata {

	public static final String BUCKET_NAME = "springBucket";

	public static final String USERNAME = "Administrator";

	public static final String PASSWORD = "password";

	public static final BucketDefinition bucketDefinition = new BucketDefinition(BUCKET_NAME);

	public static final DockerImageName COUCHBASE_IMAGE_ENTERPRISE = DockerImageName.parse("couchbase:enterprise")
		.asCompatibleSubstituteFor("couchbase/server")
		.withTag("enterprise-7.6.1");

	private CouchbaseContainerMetadata() {
		// Avoids instantiation
	}

}
