package com.wex.transactions.config;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;

@Configuration
public class AppConfig {

    // Leave blank to use the JVM default trust store (works for standard public CAs)
    // Set to a classpath resource or filesystem path to plug in a custom truststore
    // (e.g. self-signed certs or a private CA cert bundle)
    @Value("${http.client.ssl.trust-store:}")
    private String trustStorePath;

    @Value("${http.client.ssl.trust-store-password:}")
    private String trustStorePassword;

    @Value("${http.client.ssl.trust-store-type:JKS}")
    private String trustStoreType;

    @Value("${http.client.connect-timeout-ms:3000}")
    private int connectTimeoutMs;

    @Value("${http.client.read-timeout-ms:5000}")
    private int readTimeoutMs;

    @Bean
    public RestTemplate restTemplate() throws Exception {
        SSLContextBuilder sslContextBuilder = SSLContextBuilder.create();

        if (StringUtils.hasText(trustStorePath)) {
            KeyStore trustStore = KeyStore.getInstance(trustStoreType);
            try (InputStream in = openTrustStore(trustStorePath)) {
                trustStore.load(in, trustStorePassword.toCharArray());
            }
            sslContextBuilder.loadTrustMaterial(trustStore, null);
        }

        SSLContext sslContext = sslContextBuilder.build();

        SSLConnectionSocketFactory sslSocketFactory = SSLConnectionSocketFactoryBuilder.create()
                .setSslContext(sslContext)
                .build();

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(connectTimeoutMs))
                .setResponseTimeout(Timeout.ofMilliseconds(readTimeoutMs))
                .build();

        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(
                        PoolingHttpClientConnectionManagerBuilder.create()
                                .setSSLSocketFactory(sslSocketFactory)
                                .build()
                )
                .setDefaultRequestConfig(requestConfig)
                .build();

        return new RestTemplate(new HttpComponentsClientHttpRequestFactory(httpClient));
    }

    // Tries classpath first, then falls back to a filesystem path.
    private InputStream openTrustStore(String path) throws Exception {
        InputStream classpathStream = getClass().getClassLoader().getResourceAsStream(path);
        if (classpathStream != null) {
            return classpathStream;
        }
        return new FileInputStream(path);
    }
}