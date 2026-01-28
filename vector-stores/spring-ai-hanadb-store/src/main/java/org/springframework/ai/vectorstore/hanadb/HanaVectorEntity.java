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

package org.springframework.ai.vectorstore.hanadb;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

/**
 * The {@code HanaVectorEntity} is an abstract class that represents a mapped superclass
 * for entities that have a vector representation stored in a HANA vector repository. It
 * provides methods for converting the entity to JSON format and retrieving the unique
 * identifier of the entity.
 *
 * @author Rahul Mittal
 * @since 1.0.0
 */
@MappedSuperclass
public abstract class HanaVectorEntity {

	@Id
	@Column(name = "_id")
	@SuppressWarnings("NullAway.Init") // Subclasses will initialize in practice
	protected String _id;

	public HanaVectorEntity() {
	}

	public String get_id() {
		return this._id;
	}

}
