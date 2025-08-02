package com.dietdiary.diary.controller;

import com.dietdiary.diary.domain.Diary;
import com.dietdiary.diary.service.DiaryService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/diaries")
@RequiredArgsConstructor
public class DiaryController {

    private final DiaryService diaryService;

    @GetMapping
    public ResponseEntity<?> getDiariesByDate(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        
        List<Diary> diaries = diaryService.getDiariesByDate(userId, date);
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Diaries retrieved successfully");
        response.put("data", diaries);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<?> createDiary(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody Diary diary) {
        
        diary.setUserId(userId);
        Diary createdDiary = diaryService.createDiary(diary);
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Diary created successfully");
        response.put("data", createdDiary);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateDiary(
            @PathVariable Long id,
            @RequestBody Diary diaryDetails) {
        
        return diaryService.updateDiary(id, diaryDetails)
                .map(updatedDiary -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("message", "Diary updated successfully");
                    response.put("data", updatedDiary);
                    return ResponseEntity.ok(response);
                })
                .orElseGet(() -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("error", "Diary not found");
                    return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
                });
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteDiary(@PathVariable Long id) {
        boolean deleted = diaryService.deleteDiary(id);
        if (deleted) {
            Map<String, String> response = new HashMap<>();
            response.put("message", "Diary deleted successfully");
            return ResponseEntity.ok(response);
        } else {
            Map<String, String> response = new HashMap<>();
            response.put("error", "Diary not found");
            return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
        }
    }
}

@RestController
@RequestMapping("/entries")
@RequiredArgsConstructor
class DiaryEntriesController {

    private static final Logger logger = LoggerFactory.getLogger(DiaryEntriesController.class);
    private final DiaryService diaryService;

    // GET /entries/{date} - Endpoint called by Dashboard.js:38
    @GetMapping("/{date}")
    public ResponseEntity<?> getDiaryEntry(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        
        // Temporary empty response to prevent 404
        Map<String, Object> emptyEntry = new HashMap<>();
        emptyEntry.put("meals", Map.of(
            "breakfast", List.of(),
            "lunch", List.of(), 
            "dinner", List.of(),
            "snacks", List.of()
        ));
        emptyEntry.put("exercise", List.of());
        emptyEntry.put("water_intake", 0);
        emptyEntry.put("sleep_hours", 0);
        emptyEntry.put("weight", "");
        emptyEntry.put("mood", "");
        emptyEntry.put("notes", "");
        
        return ResponseEntity.ok(emptyEntry);
    }

    // POST /entries - Endpoint called by Dashboard.js:66
    @PostMapping
    public ResponseEntity<?> createDiaryEntry(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody Map<String, Object> diaryData) {
        
        logger.info("Received diary data for user {}: {}", userId, diaryData);
        return ResponseEntity.ok(diaryData);
    }
}