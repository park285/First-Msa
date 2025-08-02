package com.dietdiary.diary.repository;

import com.dietdiary.diary.domain.FreestyleDiary;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.Optional;

public interface FreestyleDiaryRepository extends JpaRepository<FreestyleDiary, Long> {
    Optional<FreestyleDiary> findByUserIdAndDate(Long userId, LocalDate date);
}
