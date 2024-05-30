package org.springframework.ai.wenxin.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.ResponseErrorHandler;

import java.io.IOException;
import java.nio.charset.Charset;

/**
 * @author lvchzh
 * @date 2024年05月24日 下午6:28
 * @description:
 */
public class CustomResponseErrorHandler implements ResponseErrorHandler {

	@Override
	public boolean hasError(ClientHttpResponse httpResponse) throws IOException {
		return (httpResponse.getStatusCode().value() == HttpStatus.Series.CLIENT_ERROR.value()
				|| httpResponse.getStatusCode().value() == HttpStatus.Series.SERVER_ERROR.value());
	}

	@Override
	public void handleError(ClientHttpResponse httpResponse) throws IOException {
		if (httpResponse.getStatusCode().value() == HttpStatus.Series.SERVER_ERROR.value()) {
			// handle SERVER_ERROR
			System.out.println("Server error: " + httpResponse.getStatusCode());
		}
		else if (httpResponse.getStatusCode().value() == HttpStatus.Series.CLIENT_ERROR.value()) {
			// handle CLIENT_ERROR
			System.out.println("Client error: " + httpResponse.getStatusCode());
		}
		// Print the response body
		System.out
			.println("Response body: " + StreamUtils.copyToString(httpResponse.getBody(), Charset.defaultCharset()));
	}

}