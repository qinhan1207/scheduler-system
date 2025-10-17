package com.qinhan.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

public class HttpClientUtil {
    private static final RestTemplate rest = new RestTemplate();
    private static final ObjectMapper mapper = new ObjectMapper();

    public static String postJson(String url, Object body) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String json = mapper.writeValueAsString(body);
        HttpEntity<String> entity = new HttpEntity<>(json, headers);
        ResponseEntity<String> resp = rest.postForEntity(url, entity, String.class);
        return resp.getBody();
    }
}
