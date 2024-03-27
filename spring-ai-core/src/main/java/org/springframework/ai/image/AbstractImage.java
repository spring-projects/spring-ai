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
package org.springframework.ai.image;

import java.util.Objects;

/**
 * An abstract class that provides a skeletal implementation of the Image interface. This
 * class is designed to be extended by specific types of images (e.g., UrlImage,
 * Base64Image, ByteArrayImage) that can be represented by different types of data.
 *
 * @param <T> the type of the image data this class handles.
 */
public abstract class AbstractImage<T> implements Image<T> {

	private final T data;

	private final ImageType type;

	/**
	 * Constructs an AbstractImage with the specified data and image type.
	 * @param data the image data of this image.
	 * @param type the type of the image, as defined by the implementation of the
	 * ImageType interface.
	 */
	protected AbstractImage(final T data, final ImageType type) {
		this.data = data;
		this.type = type;
	}

	@Override
	public T getData() {
		return this.data;
	}

	@Override
	public ImageType getType() {
		return this.type;
	}

	@Override
	public String toString() {
		return String.format("%s{type='%s', data='%s'}", getClass(), this.type.getValue(), getData());
	}

	@Override
	public boolean equals(final Object o) {
		if (this == o)
			return true;
		if (o instanceof AbstractImage<?> other)
			return Objects.equals(this.data, other.getData()) && Objects.equals(this.type, other.getType());
		return false;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.data, this.type);
	}

}
