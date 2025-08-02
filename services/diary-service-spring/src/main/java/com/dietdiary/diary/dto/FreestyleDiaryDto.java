package com.dietdiary.diary.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class FreestyleDiaryDto {
    private LocalDate date;
    private String text;
}
