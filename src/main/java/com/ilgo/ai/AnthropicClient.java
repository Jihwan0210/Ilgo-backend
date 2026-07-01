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
 * Anthropic Messages API 호출. 이미지(base64) + 프로필 텍스트를 보내고
 * assistant 응답 텍스트(JSON prefill 이후)를 반환한다.
 */
@Component
public class AnthropicClient {

    private static final Logger log = LoggerFactory.getLogger(AnthropicClient.class);
    private static final int TOO_MANY_REQUESTS = 429;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final String version;
    private final int maxTokens;
    private final double temperature;

    public AnthropicClient(RestClient anthropicRestClient,
                           ObjectMapper objectMapper,
                           @Value("${anthropic.api-key:}") String apiKey,
                           @Value("${anthropic.model:claude-opus-4-8}") String model,
                           @Value("${anthropic.base-url:https://api.anthropic.com/v1/messages}") String baseUrl,
                           @Value("${anthropic.version:2023-06-01}") String version,
                           @Value("${anthropic.max-tokens:1200}") int maxTokens,
                           @Value("${anthropic.temperature:0.2}") double temperature) {
        this.restClient = anthropicRestClient;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = baseUrl;
        this.version = version;
        this.maxTokens = maxTokens;
        this.temperature = temperature;
    }

    public String getModel() {
        return model;
    }

    /**
     * Claude 를 1회 호출하고 assistant 응답 텍스트를 반환한다.
     * 429 는 지수 백오프 후 1회 재시도하고, 그래도 실패하면 502(UPSTREAM_ERROR).
     * 그 외 오류/타임아웃도 502.
     */
    public String complete(String base64Image, String mediaType, OutputMode outputMode, String lang) {
        String body = buildRequestBody(base64Image, mediaType, outputMode, lang);
        try {
            return callOnce(body);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == TOO_MANY_REQUESTS) {
                log.warn("Anthropic 429, backing off once");
                sleep(1000L);
                try {
                    return callOnce(body);
                } catch (Exception retry) {
                    throw new ApiException(ErrorCode.RATE_LIMITED,
                            "AI 서버가 혼잡합니다. 잠시 후 다시 시도하세요.");
                }
            }
            log.error("Anthropic HTTP error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ApiException(ErrorCode.UPSTREAM_ERROR);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Anthropic call failed: {}", e.getMessage(), e);
            throw new ApiException(ErrorCode.UPSTREAM_ERROR);
        }
    }

    /**
     * 이미지 없이 텍스트만으로 Claude 호출(답장 재생성 등). 오류 처리는 {@link #complete}와 동일.
     */
    public String completeText(String system, String userText) {
        String body = buildTextBody(system, userText);
        try {
            return callOnce(body);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == TOO_MANY_REQUESTS) {
                sleep(1000L);
                try {
                    return callOnce(body);
                } catch (Exception retry) {
                    throw new ApiException(ErrorCode.RATE_LIMITED, "AI 서버가 혼잡합니다. 잠시 후 다시 시도하세요.");
                }
            }
            log.error("Anthropic HTTP error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ApiException(ErrorCode.UPSTREAM_ERROR);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Anthropic text call failed: {}", e.getMessage(), e);
            throw new ApiException(ErrorCode.UPSTREAM_ERROR);
        }
    }

    private String callOnce(String body) {
        String response = restClient.post()
                .uri(baseUrl)
                .header("x-api-key", apiKey)
                .header("anthropic-version", version)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);
        return extractText(response);
    }

    /** 응답 JSON 에서 content[0].text 추출. */
    private String extractText(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode content = root.path("content");
            if (content.isArray()) {
                for (JsonNode block : content) {
                    if ("text".equals(block.path("type").asText()) || block.has("text")) {
                        return block.path("text").asText();
                    }
                }
            }
            throw new ApiException(ErrorCode.UPSTREAM_ERROR, "AI 응답에 content 가 없습니다.");
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(ErrorCode.UPSTREAM_ERROR, "AI 응답을 읽지 못했습니다.");
        }
    }

    private String buildRequestBody(String base64Image, String mediaType, OutputMode outputMode, String lang) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", model);
        root.put("max_tokens", maxTokens);
        root.put("temperature", temperature);
        root.put("system", ClaudePrompt.SYSTEM);

        ArrayNode messages = root.putArray("messages");

        // user: [image, text]
        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        ArrayNode userContent = userMsg.putArray("content");

        ObjectNode imageBlock = userContent.addObject();
        imageBlock.put("type", "image");
        ObjectNode source = imageBlock.putObject("source");
        source.put("type", "base64");
        source.put("media_type", mediaType);
        source.put("data", base64Image);

        ObjectNode textBlock = userContent.addObject();
        textBlock.put("type", "text");
        textBlock.put("text", ClaudePrompt.userText(outputMode, lang));

        // assistant prefill: "{"  → JSON 강제
        ObjectNode assistantMsg = messages.addObject();
        assistantMsg.put("role", "assistant");
        assistantMsg.put("content", "{");

        return writeJson(root);
    }

    private String buildTextBody(String system, String userText) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", model);
        root.put("max_tokens", maxTokens);
        root.put("temperature", temperature);
        root.put("system", system);

        ArrayNode messages = root.putArray("messages");
        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", userText);

        ObjectNode assistantMsg = messages.addObject();
        assistantMsg.put("role", "assistant");
        assistantMsg.put("content", "{");

        return writeJson(root);
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
