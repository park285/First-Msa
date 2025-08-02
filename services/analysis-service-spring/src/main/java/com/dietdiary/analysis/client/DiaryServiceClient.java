package com.dietdiary.analysis.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@FeignClient(name = "diary-service", url = "${diary.service.url}")
public interface DiaryServiceClient {

    @GetMapping("/diaries")
    Map<String, Object> getDiariesByDate(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam("date") String date,
            @RequestHeader("X-Service-Key") String serviceKey);
}
