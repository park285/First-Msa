package com.dietdiary.analysis.controller;

import com.dietdiary.analysis.client.DiaryServiceClient;
import com.dietdiary.analysis.dto.FreestyleAnalysisRequest;
import com.dietdiary.analysis.service.GeminiService;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/analysis")
@RequiredArgsConstructor
public class AnalysisController {

    private final DiaryServiceClient diaryServiceClient;
    private final GeminiService geminiService;

    @Value("${SERVICE_API_KEY}")
    private String serviceKey;

    @GetMapping
    public ResponseEntity<?> getAnalysis(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam("date") String date) {

        try {
            Map<String, Object> diaryResponse = diaryServiceClient.getDiariesByDate(userId, date, serviceKey);
            List<Map<String, Object>> diaries = (List<Map<String, Object>>) diaryResponse.get("data");

            if (diaries == null || diaries.isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("message", "No diary entries found for the given date.");
                return ResponseEntity.ok(response);
            }

            String analysis = geminiService.getAnalysis(diaries);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Analysis retrieved successfully");
            response.put("data", analysis);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("error", "Failed to get analysis: " + e.getMessage());
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/freestyle")
    public ResponseEntity<?> getFreestyleAnalysis(@RequestBody FreestyleAnalysisRequest request) {
        try {
            String analysis = geminiService.getFreestyleAnalysis(request.getText());
            Map<String, Object> response = new HashMap<>();
            response.put("analysis", analysis);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("error", "Failed to get freestyle analysis: " + e.getMessage());
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
