package com.dietdiary.diary.controller;

import com.dietdiary.diary.domain.FreestyleDiary;
import com.dietdiary.diary.dto.FreestyleDiaryDto;
import com.dietdiary.diary.service.FreestyleDiaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/freestyle")
@RequiredArgsConstructor
public class FreestyleDiaryController {

    private final FreestyleDiaryService service;

    @GetMapping
    public ResponseEntity<?> getDiary(@RequestHeader("X-User-Id") Long userId, @RequestParam("date") String date) {
        LocalDate localDate = LocalDate.parse(date);
        return service.getDiary(userId, localDate)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.ok(Map.of("text", "")));
    }

    @PostMapping
    public ResponseEntity<FreestyleDiary> saveDiary(@RequestHeader("X-User-Id") Long userId, @RequestBody FreestyleDiaryDto dto) {
        FreestyleDiary savedDiary = service.saveDiary(userId, dto.getDate(), dto.getText());
        return ResponseEntity.ok(savedDiary);
    }
}
