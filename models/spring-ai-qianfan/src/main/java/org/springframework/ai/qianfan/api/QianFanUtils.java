package org.springframework.ai.qianfan.api;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.function.Consumer;

public class QianFanUtils {

	public static Consumer<HttpHeaders> defaultHeaders() {
		return headers -> headers.setContentType(MediaType.APPLICATION_JSON);
	}

}
