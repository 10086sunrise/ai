package com.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public class WeatherService {

    private static final String API_KEY = "48e57ebc0a772966223e425e090b2688"; // ← 替换为你自己的 Juhe Key
    private static final String BASE_URL = "http://apis.juhe.cn/simpleWeather/query";

    private final OkHttpClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public WeatherService() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    public String getWeather(String city) throws IOException {
        if (city == null || city.trim().isEmpty()) {
            throw new IllegalArgumentException("城市名称不能为空");
        }

        // URL 编码城市名（虽然中文通常可直接传，但更安全）
        String encodedCity = URLEncoder.encode(city.trim(), StandardCharsets.UTF_8);
        String url = BASE_URL + "?city=" + encodedCity + "&key=" + API_KEY;

        Request request = new Request.Builder().url(url).build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("天气 API 请求失败，HTTP 状态码: " + response.code());
            }

            String responseBody = response.body().string();
            JsonNode root = mapper.readTree(responseBody);

            int errorCode = root.get("error_code").asInt();
            if (errorCode != 0) {
                String reason = root.has("reason") ? root.get("reason").asText() : "未知错误";
                throw new IOException("聚合数据 API 返回错误: " + reason + " (code: " + errorCode + ")");
            }

            JsonNode result = root.get("result");
            if (result == null || !result.has("realtime")) {
                throw new IOException("API 响应缺少实时天气数据");
            }

            JsonNode realtime = result.get("realtime");
            String temperature = realtime.get("temperature").asText();
            String humidity = realtime.get("humidity").asText();
            String info = realtime.get("info").asText();
            String wind = realtime.has("direct") ? realtime.get("direct").asText() : "";

            StringBuilder sb = new StringBuilder();
            sb.append("🌤️ ").append(city).append(" 当前天气：").append(info);
            sb.append("，温度 ").append(temperature).append("℃");
            sb.append("，湿度 ").append(humidity).append("%");
            if (!wind.isEmpty()) {
                sb.append("，").append(wind);
            }

            return sb.toString();
        } catch (Exception e) {
            if (e instanceof IOException) throw e;
            throw new IOException("获取天气信息时发生异常", e);
        }
    }
}