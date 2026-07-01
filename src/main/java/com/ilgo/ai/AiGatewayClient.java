package com.ilgo.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ilgo.common.ApiException;
import com.ilgo.common.ErrorCode;
import com.ilgo.user.OutputMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * OpenAI 호환 게이트웨이(Chat Completions) 호출.
 * 이미지(base64 data URI) + 지시 텍스트를 보내고 assistant content 텍스트(JSON)를 반환한다.
 */
@Component
public class AiGatewayClient {

    private static final Logger log = LoggerFactory.getLogger(AiGatewayClient.class);
    private static final int TOO_MANY_REQUESTS = 429;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final int maxTokens;
    private final double temperature;

    public AiGatewayClient(RestClient aiRestClient,
                           ObjectMapper objectMapper,
                           @Value("${ai.api-key:}") String apiKey,
                           @Value("${ai.model:gemini-3-flash-preview}") String model,
                           @Value("${ai.base-url:https://factchat-cloud.mindlogic.ai/v1/gateway/chat/completions}") String baseUrl,
                           @Value("${ai.max-tokens:1200}") int maxTokens,
                           @Value("${ai.temperature:0.2}") double temperature) {
        this.restClient = aiRestClient;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = baseUrl;
        this.maxTokens = maxTokens;
        this.temperature = temperature;
    }

    public String getModel() {
        return model;
    }

    /**
     * 이미지 + 프로필 텍스트로 게이트웨이를 1회 호출하고 assistant content 를 반환한다.
     * 429 는 지수 백오프 후 1회 재시도, 그래도 실패하면 502(UPSTREAM_ERROR). 그 외 오류/타임아웃도 502.
     */
    public String complete(String base64Image, String mediaType, OutputMode outputMode, String lang) {
        String body = buildImageBody(base64Image, mediaType, outputMode, lang);
        return send(body);
    }

    /** 이미지 없이 텍스트만으로 호출(답장 재생성 등). */
    public String completeText(String system, String userText) {
        String body = buildTextBody(system, userText);
        return send(body);
    }

    private String send(String body) {
        try {
            return callOnce(body);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == TOO_MANY_REQUESTS) {
                log.warn("AI gateway 429, backing off once");
                sleep(1000L);
                try {
                    return callOnce(body);
                } catch (Exception retry) {
                    throw new ApiException(ErrorCode.UPSTREAM_ERROR);
                }
            }
            log.error("AI gateway HTTP error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ApiException(ErrorCode.UPSTREAM_ERROR);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI gateway call failed: {}", e.getMessage(), e);
            throw new ApiException(ErrorCode.UPSTREAM_ERROR);
        }
    }

    private String callOnce(String body) {
        String response = restClient.post()
                .uri(baseUrl)
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);
        return extractContent(response);
    }

    /** OpenAI 응답에서 choices[0].message.content 추출. */
    private String extractContent(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isTextual()) {
                return content.asText();
            }
            // 일부 게이트웨이는 content 를 배열로 줄 수 있음
            if (content.isArray()) {
                StringBuilder sb = new StringBuilder();
                for (JsonNode part : content) {
                    if (part.has("text")) {
                        sb.append(part.path("text").asText());
                    }
                }
                if (sb.length() > 0) {
                    return sb.toString();
                }
            }
            throw new ApiException(ErrorCode.UPSTREAM_ERROR, "AI 응답에 content 가 없습니다.");
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(ErrorCode.UPSTREAM_ERROR, "AI 응답을 읽지 못했습니다.");
        }
    }

    private String buildImageBody(String base64Image, String mediaType, OutputMode outputMode, String lang) {
        ObjectNode root = baseBody();
        ArrayNode messages = root.putArray("messages");

        ObjectNode system = messages.addObject();
        system.put("role", "system");
        system.put("content", AiPrompt.SYSTEM);

        ObjectNode user = messages.addObject();
        user.put("role", "user");
        ArrayNode content = user.putArray("content");

        ObjectNode textPart = content.addObject();
        textPart.put("type", "text");
        textPart.put("text", AiPrompt.userText(outputMode, lang));

        ObjectNode imagePart = content.addObject();
        imagePart.put("type", "image_url");
        ObjectNode imageUrl = imagePart.putObject("image_url");
        imageUrl.put("url", "data:" + mediaType + ";base64," + base64Image);

        return writeJson(root);
    }

    private String buildTextBody(String systemPrompt, String userText) {
        ObjectNode root = baseBody();
        ArrayNode messages = root.putArray("messages");

        ObjectNode system = messages.addObject();
        system.put("role", "system");
        system.put("content", systemPrompt);

        ObjectNode user = messages.addObject();
        user.put("role", "user");
        user.put("content", userText);

        return writeJson(root);
    }

    private ObjectNode baseBody() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", model);
        root.put("max_tokens", maxTokens);
        root.put("temperature", temperature);
        return root;
    }

    private String writeJson(ObjectNode root) {
        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "요청 본문 생성 실패");
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
