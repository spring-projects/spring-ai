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
package org.springframework.ai.autoconfigure.vectorstore.hanadb;

import org.junit.Test;
import org.junit.jupiter.api.Assertions;

/**
 * @author Rahul Mittal
 */
public class HanaCloudVectorStorePropertiesTest {

	@Test
	public void testHanaCloudVectorStoreProperties() {
		var props = new HanaCloudVectorStoreProperties();
		props.setTableName("CRICKET_WORLD_CUP");
		props.setTopK(5);

		Assertions.assertEquals("CRICKET_WORLD_CUP", props.getTableName());
		Assertions.assertEquals(5, props.getTopK());
	}

}
