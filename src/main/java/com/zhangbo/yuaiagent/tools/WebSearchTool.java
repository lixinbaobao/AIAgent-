package com.zhangbo.yuaiagent.tools;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 网页搜索工具
 */
public class WebSearchTool {

    // SearchAPI 的搜索接口地址
    private static final String SEARCH_API_URL = "https://www.searchapi.io/api/v1/search";

    private final String apiKey;

    public WebSearchTool(String apiKey) {
        this.apiKey = apiKey;
    }

    @Tool(description = "Search for information from Baidu Search Engine")
    public String searchWeb(
            @ToolParam(description = "Search query keyword") String query) {
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("q", query);
        paramMap.put("api_key", apiKey);
        paramMap.put("engine", "baidu");
        try {
            String response = HttpUtil.get(SEARCH_API_URL, paramMap);
            // 取出返回结果的前 5 条
            JSONObject jsonObject = JSONUtil.parseObj(response);
            // 提取 organic_results 部分
            JSONArray organicResults = jsonObject.getJSONArray("organic_results");
            if (organicResults == null || organicResults.isEmpty()) {
                return "No results found";
            }
            List<Object> objects = organicResults.subList(0, Math.min(5, organicResults.size()));
            // 拼接搜索结果为字符串
            String result = objects.stream().map(obj -> {
                JSONObject tmpJSONObject = (JSONObject) obj;
                return tmpJSONObject.toString();
            }).collect(Collectors.joining(","));
            return result;
        } catch (Exception e) {
            return "Error searching Baidu: " + e.getMessage();
        }
    }

    @Tool(description = "Search for latest news. Use this when user asks about news, current events, or recent happenings. Always returns the most recent news articles with dates.")
    public String searchNews(
            @ToolParam(description = "News search query keyword") String query) {
        // 获取当前日期
        LocalDate today = LocalDate.now();
        String todayStr = today.format(DateTimeFormatter.ofPattern("yyyy年M月d日"));
        String yearMonth = today.format(DateTimeFormatter.ofPattern("yyyy年M月"));

        // 构建包含当前日期的查询，强制获取最新结果
        String newsQuery = String.format("%s %s 最新新闻", query, todayStr);

        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("q", newsQuery);
        paramMap.put("api_key", apiKey);
        // 使用 Google 搜索（通常更新更快）
        paramMap.put("engine", "google");
        // 按日期排序，优先显示最新结果
        paramMap.put("sort", "date:d");
        // 限制结果数量
        paramMap.put("num", 10);

        try {
            String response = HttpUtil.get(SEARCH_API_URL, paramMap);
            JSONObject jsonObject = JSONUtil.parseObj(response);

            // 提取搜索结果
            JSONArray organicResults = jsonObject.getJSONArray("organic_results");
            if (organicResults == null || organicResults.isEmpty()) {
                // 尝试使用百度搜索
                return searchNewsWithBaidu(query);
            }

            // 过滤并格式化结果，优先显示带日期的结果
            List<Object> objects = organicResults.subList(0, Math.min(5, organicResults.size()));
            StringBuilder resultBuilder = new StringBuilder();
            resultBuilder.append(String.format("搜索时间：%s\n\n", todayStr));

            for (int i = 0; i < objects.size(); i++) {
                JSONObject item = (JSONObject) objects.get(i);
                String title = item.getStr("title", "");
                String link = item.getStr("link", "");
                String snippet = item.getStr("snippet", "");
                String date = item.getStr("date", "");

                resultBuilder.append(String.format("%d. %s\n", i + 1, title));
                if (!date.isEmpty()) {
                    resultBuilder.append(String.format("   日期：%s\n", date));
                }
                resultBuilder.append(String.format("   摘要：%s\n", snippet));
                resultBuilder.append(String.format("   链接：%s\n\n", link));
            }

            return resultBuilder.toString();
        } catch (Exception e) {
            return searchNewsWithBaidu(query);
        }
    }

    /**
     * 使用百度搜索新闻（备选方案）
     */
    private String searchNewsWithBaidu(String query) {
        LocalDate today = LocalDate.now();
        String todayStr = today.format(DateTimeFormatter.ofPattern("yyyy年M月d日"));

        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("q", query + " " + todayStr + " 最新");
        paramMap.put("api_key", apiKey);
        paramMap.put("engine", "baidu");

        try {
            String response = HttpUtil.get(SEARCH_API_URL, paramMap);
            JSONObject jsonObject = JSONUtil.parseObj(response);
            JSONArray organicResults = jsonObject.getJSONArray("organic_results");

            if (organicResults == null || organicResults.isEmpty()) {
                return String.format("未找到关于'%s'的最新新闻。请尝试其他关键词。", query);
            }

            List<Object> objects = organicResults.subList(0, Math.min(5, organicResults.size()));
            StringBuilder resultBuilder = new StringBuilder();
            resultBuilder.append(String.format("搜索时间：%s\n\n", todayStr));

            for (int i = 0; i < objects.size(); i++) {
                JSONObject item = (JSONObject) objects.get(i);
                String title = item.getStr("title", "");
                String link = item.getStr("link", "");
                String snippet = item.getStr("snippet", "");

                resultBuilder.append(String.format("%d. %s\n", i + 1, title));
                resultBuilder.append(String.format("   摘要：%s\n", snippet));
                resultBuilder.append(String.format("   链接：%s\n\n", link));
            }

            return resultBuilder.toString();
        } catch (Exception e) {
            return String.format("搜索新闻时出错：%s。当前日期：%s", e.getMessage(), todayStr);
        }
    }
}
