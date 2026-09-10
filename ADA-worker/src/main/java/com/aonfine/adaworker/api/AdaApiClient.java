package com.aonfine.adaworker.api;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Client for the internal Worker API (/worker/analysis/*) only -- never the user-session API.
 * Auth is a Bearer token from WorkerConfig (sourced from ADA_WORKER_API_TOKEN), sent only as an
 * HTTP header, never logged, never placed in a URL query string.
 */
public final class AdaApiClient {
    private final String baseUrl;
    private final String apiToken;
    private final HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Duration timeout;

    public AdaApiClient(String baseUrl, String apiToken, int timeoutSeconds) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiToken = apiToken;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    public ClaimResponse claim(String workerId) throws IOException, InterruptedException {
        return post("/worker/analysis/claim.do", Map.of("workerId", workerId), ClaimResponse.class);
    }

    public AnalysisApiResult heartbeat(String attemptId, String workerId) throws IOException, InterruptedException {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("attemptId", attemptId);
        form.put("workerId", workerId);
        return post("/worker/analysis/heartbeat.do", form, AnalysisApiResult.class);
    }

    public AnalysisApiResult report(String jobId, String attemptId, String workerId, long expectedJobVersion,
            long expectedAttemptVersion, String expectedState, String newState, String failCode, String failReason,
            String resultPath) throws IOException, InterruptedException {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("jobId", jobId);
        form.put("attemptId", attemptId);
        form.put("workerId", workerId);
        form.put("expectedJobVersion", String.valueOf(expectedJobVersion));
        form.put("expectedAttemptVersion", String.valueOf(expectedAttemptVersion));
        form.put("expectedState", expectedState);
        form.put("newState", newState);
        if (failCode != null) form.put("failCode", failCode);
        if (failReason != null) form.put("failReason", failReason);
        if (resultPath != null) form.put("resultPath", resultPath);
        return post("/worker/analysis/report.do", form, AnalysisApiResult.class);
    }

    private <T> T post(String path, Map<String, String> form, Class<T> type) throws IOException, InterruptedException {
        String body = form.entrySet().stream()
                .map(e -> urlEncode(e.getKey()) + "=" + urlEncode(e.getValue()))
                .collect(Collectors.joining("&"));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(timeout)
                .header("Authorization", "Bearer " + apiToken)
                .header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 500) {
            throw new IOException("ADA worker API returned server error " + response.statusCode() + " for " + path);
        }
        return mapper.readValue(response.body(), type);
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
