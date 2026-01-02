package com.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class NewsService {
    private static final String API_KEY = "YOUR_NEWSAPI_KEY"; // ← 替换为你自己的 Key
    private static final String BASE_URL = "https://newsapi.org/v2/top-headlines?country=cn&apiKey=" + API_KEY + "&category=";
    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    public String getNews(String category) throws IOException {
        String url = BASE_URL + (category == null ? "general" : category);
        Request request = new Request.Builder().url(url).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("新闻 API 失败");
            JsonNode root = mapper.readTree(response.body().string());
            List<String> titles = new ArrayList<>();
            var articles = root.get("articles");
            for (int i = 0; i < Math.min(3, articles.size()); i++) {
                titles.add("• " + articles.get(i).get("title").asText());
            }
            return "📰 最新" + (category == null ? "新闻" : category) + "：\n" + String.join("\n", titles);
        }
    }
}