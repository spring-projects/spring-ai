package org.springframework1.ai.model.function;

import org.springframework.ai.model.function.FunctionCalling;

import java.time.LocalDateTime;

public  class FunctionCallConfig {
		@FunctionCalling(name = "dateTime", description = "get the current date and time")
		public String dateTime(String location) {
			return location + " dateTime:" + LocalDateTime.now();
		}

}