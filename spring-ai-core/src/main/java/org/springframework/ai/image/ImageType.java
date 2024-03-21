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
 * A generic interface for defining types of images in an extensible enum pattern. This
 * interface allows for the creation of type-safe enums that can be extended to include
 * new types of image references.
 *
 * @param <T> The concrete enum type that extends Enum and implements this interface. This
 * type parameter allows the enum to be part of the extensible enum pattern and to use
 * methods defined in this interface in a type-safe manner.
 * @param <E> The type of the value associated with the enum constants. It represents the
 * data type of the values that each enum constant holds.
 * <p>
 * <strong>Example Usage:</strong>
 * </p>
 * <pre>
 * // Example of an enum using String constants
 * public enum DefaultImageType implements ImageType&lt;DefaultImageType, String&gt; {
 *           URL("url"),
 *           BASE64("b64");
 *           ...
 * }
 *
 * // Example of an enum using Integer constants
 * public enum IntConstType implements ImageType&lt;IntConstType, Integer&gt; {
 *           URL(1),
 *           BASE64(2);
 *           ...
 * }
 * </pre>
 */
public interface ImageType<T extends Enum<T> & ImageType<T, E>, E> {

	/**
	 * Returns the value associated with the enum constant.
	 * @return The value of the enum constant of type {@code E}.
	 */
	E getValue();

}
