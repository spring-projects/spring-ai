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

/**
 * An extensible interface for handling various types of image data.
 *
 * @param <T> The type of the image data this interface deals with. For instance, it could
 * be a byte array, a file path, or a Base64 encoded string of the image.
 * @author youngmon
 * @version 0.8.1
 */
public interface Image<T> {

	/**
	 * Returns the image data. The type of the data is determined by the type parameter
	 * {@code T} of this interface.
	 * @return Image data of type T.
	 */
	T getData();

	/**
	 * Returns the type of the image. The image type is defined by the {@link ImageType}.
	 * Through this method, it's possible to know the type of the image.
	 * @return The {@link ImageType} of the image.
	 */
	ImageType getType();

}
