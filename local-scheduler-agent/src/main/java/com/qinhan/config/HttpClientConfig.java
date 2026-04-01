package com.qinhan.config;

import javax.annotation.Resource;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import com.qinhan.properties.HttpClientProperties;

/**
 * 使用Apache HttpClient连接池
 */
@Configuration
public class HttpClientConfig {

    private final HttpClientProperties properties;


    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate(new HttpComponentsClientHttpRequestFactory(httpClient()));
    }

    public HttpClientConfig(HttpClientProperties properties){
        this.properties = properties;
    }

    private HttpClient httpClient() {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(properties.getConnectTimeout()))
                .setResponseTimeout(Timeout.ofMilliseconds(properties.getReadTimeout()))
                .build();

        PoolingHttpClientConnectionManager connectionManager =
                new PoolingHttpClientConnectionManager();

        return HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .setConnectionManager(connectionManager)
                .evictIdleConnections(Timeout.ofSeconds(30))
                .build();
    }
}