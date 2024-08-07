/*
 * Copyright 2024 - 2024 the original author or authors.
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
package org.springframework.ai.model.function;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

public class FunctionCallbackMethodProcessorIT {

	private static final Logger logger = LoggerFactory.getLogger(FunctionCallbackMethodProcessorIT.class);


	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withUserConfiguration(FunctionCallConfig.class)
			.withBean(FunctionCallbackContext.class)
			.withBean(FunctionCallbackMethodProcessor.class);

	 @Test
	 public void testFunctionCallbackMethodProcessor() {
		 contextRunner.run(context -> {
			 FunctionCallbackContext callbackContext = context.getBean(FunctionCallbackContext.class);
			 FunctionCallback functionCallback = callbackContext.getFunctionCallback("dateTime", null);
			 logger.info("FunctionCallback: name:{}, description:{}",
					 functionCallback.getName(), functionCallback.getDescription());
			 String result = functionCallback.call("{\"location\":\"New York\"}");
			 logger.info("Result: {}", result);
			 assert result.contains("New York");
		 });
	 }

}
