package com.dietdiary.analysis.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GeminiService {

    @Value("${gemini.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String getAnalysis(List<Map<String, Object>> diaries) throws JsonProcessingException {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent?key=" + apiKey;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String prompt = createPrompt(diaries);

        Map<String, Object> textPart = new HashMap<>();
        textPart.put("text", prompt);

        Map<String, Object> content = new HashMap<>();
        content.put("parts", Collections.singletonList(textPart));

        Map<String, Object> body = new HashMap<>();
        body.put("contents", Collections.singletonList(content));

        HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);

        Map<String, Object> response = restTemplate.postForObject(url, request, Map.class);
        
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            return "{\"error\": \"Failed to parse Gemini response\"}";
        }
    }

    private String createPrompt(List<Map<String, Object>> diaries) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Analyze the following diet diary for a specific day and provide a detailed analysis and recommendations. The user's goal is a healthy diet. The analysis should be in Korean.\n\n");
        prompt.append("Diet entries:\n");

        for (Map<String, Object> diary : diaries) {
            prompt.append(String.format("- Meal: %s, Food: %s, Calories: %.2f\n",
                    diary.get("mealType"), diary.get("foodName"), diary.get("calories")));
        }

        prompt.append("\nPlease provide:\n");
        prompt.append("1. A summary of the total calorie intake.\n");
        prompt.append("2. An analysis of the nutritional balance (carbohydrates, protein, fat).\n");
        prompt.append("3. Recommendations for improvement (e.g., suggest healthier alternatives, portion control advice).\n");
        prompt.append("4. A sample healthy meal plan for the next day.\n");

        return prompt.toString();
    }

    public String getFreestyleAnalysis(String text) throws JsonProcessingException {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent?key=" + apiKey;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String prompt = createFreestylePrompt(text);

        Map<String, Object> textPart = new HashMap<>();
        textPart.put("text", prompt);

        Map<String, Object> content = new HashMap<>();
        content.put("parts", Collections.singletonList(textPart));

        Map<String, Object> body = new HashMap<>();
        body.put("contents", Collections.singletonList(content));

        HttpEntity<String> requestEntity = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);

        Map<String, Object> response = restTemplate.postForObject(url, requestEntity, Map.class);

        // Extract the generated text from the response
        try {
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
            if (candidates != null && !candidates.isEmpty()) {
                Map<String, Object> candidate = candidates.get(0);
                Map<String, Object> contentMap = (Map<String, Object>) candidate.get("content");
                List<Map<String, Object>> parts = (List<Map<String, Object>>) contentMap.get("parts");
                if (parts != null && !parts.isEmpty()) {
                    return (String) parts.get(0).get("text");
                }
            }
            return "{\"error\": \"Failed to extract analysis from Gemini response\"}";
        } catch (Exception e) {
            return "{\"error\": \"Failed to parse Gemini response\"}";
        }
    }

    private String createFreestylePrompt(String text) {
        return "다음은 사용자가 작성한 하루 일기입니다. 사용자의 식단, 운동, 일상 생활에 대해 종합적으로 분석하고 평가해주세요. 평가는 한국어로 작성해주세요. " +
                "결과는 다음 항목을 포함해야 합니다:\n" +
                "1. **총평**: 하루 전반에 대한 긍정적인 피드백과 격려.\n" +
                "2. **식단 분석**: 식단의 장점과 개선점을 구체적으로 분석해주세요. 칼로리, 영양소 균형 (탄수화물, 단백질, 지방)을 고려하고, 더 건강한 식단을 위한 추천 메뉴를 제안해주세요.\n" +
                "3. **운동 및 활동 분석**: 운동의 종류와 강도가 적절했는지 평가하고, 건강 목표에 맞는 추가적인 활동을 제안해주세요.\n" +
                "4. **생활 습관 및 감정**: 사용자의 기분이나 생활 패턴에서 긍정적인 점이나 개선할 점을 찾아 조언해주세요.\n" +
                "5. **내일을 위한 팁**: 내일 실천할 수 있는 간단하고 구체적인 팁 한 가지를 제안해주세요.\n\n" +
                "--- 사용자 일기 내용 ---\n" +
                text +
                "\n--- 분석 시작 ---";
    }
}
