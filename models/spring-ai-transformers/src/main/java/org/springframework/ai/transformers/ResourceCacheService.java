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

package org.springframework.ai.transformers;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.FileUrlResource;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

/**
 * Service that helps caching remote {@link Resource}s on the local file system.
 *
 * @author Christian Tzolov
 */
public class ResourceCacheService {

	private static final Log logger = LogFactory.getLog(ResourceCacheService.class);

	/**
	 * The parent folder that contains all cached resources.
	 */
	private final File cacheDirectory;

	/**
	 * Resources with URI schemas belonging to the excludedUriSchemas are not cached. By
	 * default, the file and classpath resources are not cached as they are already in the
	 * local file system.
	 */
	private List<String> excludedUriSchemas = new ArrayList<>(List.of("file", "classpath"));

	public ResourceCacheService() {
		this(new File(System.getProperty("java.io.tmpdir"), "spring-ai-onnx-generative").getAbsolutePath());
	}

	public ResourceCacheService(String rootCacheDirectory) {
		this(new File(rootCacheDirectory));
	}

	public ResourceCacheService(File rootCacheDirectory) {
		Assert.notNull(rootCacheDirectory, "Cache directory can not be null.");
		this.cacheDirectory = rootCacheDirectory;
		if (!this.cacheDirectory.exists()) {
			logger.info("Create cache root directory: " + this.cacheDirectory.getAbsolutePath());
			this.cacheDirectory.mkdirs();
		}
		Assert.isTrue(this.cacheDirectory.isDirectory(), "The cache folder must be a directory");
	}

	/**
	 * Overrides the excluded URI schemas list.
	 * @param excludedUriSchemas new list of URI schemas to be excluded from caching.
	 */
	public void setExcludedUriSchemas(List<String> excludedUriSchemas) {
		Assert.notNull(excludedUriSchemas, "The excluded URI schemas list can not be null");
		this.excludedUriSchemas = excludedUriSchemas;
	}

	/**
	 * Get {@link Resource} representing the cached copy of the original resource.
	 * @param originalResourceUri Resource to be cached.
	 * @return Returns a cached resource. If the original resource's URI schema is within
	 * the excluded schema list the original resource is returned.
	 */
	public Resource getCachedResource(String originalResourceUri) {
		return this.getCachedResource(new DefaultResourceLoader().getResource(originalResourceUri));
	}

	/**
	 * Get {@link Resource} representing the cached copy of the original resource.
	 * @param originalResource Resource to be cached.
	 * @return Returns a cached resource. If the original resource's URI schema is within
	 * the excluded schema list the original resource is returned.
	 */
	public Resource getCachedResource(Resource originalResource) {
		try {
			if (this.excludedUriSchemas.contains(originalResource.getURI().getScheme())) {
				logger.info("The " + originalResource.toString() + " resource with URI schema ["
						+ originalResource.getURI().getScheme() + "] is excluded from caching");
				return originalResource;
			}

			File cachedFile = getCachedFile(originalResource);
			if (!cachedFile.exists()) {
				FileCopyUtils.copy(StreamUtils.copyToByteArray(originalResource.getInputStream()), cachedFile);
				logger.info("Caching the " + originalResource.toString() + " resource to: " + cachedFile);
			}
			return new FileUrlResource(cachedFile.getAbsolutePath());
		}
		catch (Exception e) {
			throw new IllegalStateException("Failed to cache the resource: " + originalResource.getDescription(), e);
		}
	}

	private File getCachedFile(Resource originalResource) throws IOException {
		var resourceParentFolder = new File(this.cacheDirectory,
				UUID.nameUUIDFromBytes(pathWithoutLastSegment(originalResource.getURI())).toString());
		resourceParentFolder.mkdirs();
		String newFileName = getCacheName(originalResource);
		return new File(resourceParentFolder, newFileName);
	}

	private byte[] pathWithoutLastSegment(URI uri) {
		String path = uri.toASCIIString();
		var pathBeforeLastSegment = path.substring(0, path.lastIndexOf('/') + 1);
		return pathBeforeLastSegment.getBytes();
	}

	private String getCacheName(Resource originalResource) throws IOException {
		String fileName = originalResource.getFilename();
		Assert.hasText(fileName, "The file name must should not be null or empty");
		String fragment = originalResource.getURI().getFragment();
		return !StringUtils.hasText(fragment) ? fileName : fileName + "_" + fragment;
	}

	public void deleteCacheFolder() {
		if (this.cacheDirectory.exists()) {
			logger.info("Empty Model Cache at:" + this.cacheDirectory.getAbsolutePath());
			this.cacheDirectory.delete();
			this.cacheDirectory.mkdirs();
		}
	}

}
