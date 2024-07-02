package org.springframework.ai.autoconfigure.wenxin;

/**
 * @author lvchzh
 * @since 1.0.0
 */
public class WenxinParentProperties {

	private String baseUrl;

	private String accessKey;

	private String secretKey;

	public String getBaseUrl() {
		return baseUrl;
	}

	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}

	public String getAccessKey() {
		return accessKey;
	}

	public void setAccessKey(String accessKey) {
		this.accessKey = accessKey;
	}

	public String getSecretKey() {
		return secretKey;
	}

	public void setSecretKey(String secretKey) {
		this.secretKey = secretKey;
	}

}
