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

package org.springframework.ai.image;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

public class Image {

	/**
	 * The URL where the image can be accessed.
	 */
	private @Nullable String url;

	/**
	 * Base64 encoded image string.
	 */
	private @Nullable String b64Json;

	public Image(@Nullable String url, @Nullable String b64Json) {
		this.url = url;
		this.b64Json = b64Json;
	}

	public @Nullable String getUrl() {
		return this.url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public @Nullable String getB64Json() {
		return this.b64Json;
	}

	public void setB64Json(String b64Json) {
		this.b64Json = b64Json;
	}

	@Override
	public String toString() {
		return "Image{" + "url='" + this.url + '\'' + ", b64Json='" + this.b64Json + '\'' + '}';
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof Image image)) {
			return false;
		}
		return Objects.equals(this.url, image.url) && Objects.equals(this.b64Json, image.b64Json);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.url, this.b64Json);
	}

}
