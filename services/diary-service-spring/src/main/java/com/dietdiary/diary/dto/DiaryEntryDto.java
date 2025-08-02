package com.dietdiary.diary.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class DiaryEntryDto {
    
    @JsonProperty("entry_date")
    private LocalDate entryDate;
    
    private Map<String, List<MealItem>> meals;
    private List<ExerciseItem> exercise;
    
    @JsonProperty("water_intake")
    private Integer waterIntake;
    
    @JsonProperty("sleep_hours")
    private Double sleepHours;
    
    private Double weight;
    private Integer mood;
    private String notes;
    
    @Getter
    @Setter
    public static class MealItem {
        private String food;
        private Integer calories;
    }
    
    @Getter
    @Setter
    public static class ExerciseItem {
        private String name;
        private Integer duration;
    }
}