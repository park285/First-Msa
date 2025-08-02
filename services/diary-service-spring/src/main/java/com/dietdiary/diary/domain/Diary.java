package com.dietdiary.diary.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "diaries")
@Getter
@Setter
public class Diary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private String mealType; // e.g., "breakfast", "lunch", "dinner"

    @Column(nullable = false, length = 500)
    private String foodName;

    @Column(nullable = false)
    private Double calories;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
