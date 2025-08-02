package com.dietdiary.analysis.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FreestyleAnalysisRequest {
    private String date;
    private String text;
}
