package org.springframework.ai.dashscope.api;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.function.Consumer;

/**
 * @author Nottyjay Ji
 */
public class DashscopeApiUtils {

	public static final String DEFAULT_BASE_URL = "https://dashscope.aliyuncs.com";

	public static Consumer<HttpHeaders> getJsonContentHeaders(String apiKey) {
		return (headers) -> {
			headers.setBearerAuth(apiKey);
			headers.setContentType(MediaType.APPLICATION_JSON);
		};
	}

}
