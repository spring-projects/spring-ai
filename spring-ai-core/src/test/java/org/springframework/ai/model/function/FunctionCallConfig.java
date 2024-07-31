package org.springframework.ai.model.function;

import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

@Configuration
public  class FunctionCallConfig {

		@FunctionCalling(name = "dateTime", description = "get the current date and time")
		public String dateTime(String location) {
			return location + " dateTime:" + LocalDateTime.now();
		}

}