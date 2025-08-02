package com.dietdiary.diary.service;

import com.dietdiary.diary.domain.Diary;
import com.dietdiary.diary.repository.DiaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DiaryService {

    private final DiaryRepository diaryRepository;

    public List<Diary> getDiariesByDate(Long userId, LocalDate date) {
        return diaryRepository.findByUserIdAndDate(userId, date);
    }

    @Transactional
    public Diary createDiary(Diary diary) {
        return diaryRepository.save(diary);
    }

    @Transactional
    public Optional<Diary> updateDiary(Long id, Diary updatedDiary) {
        return diaryRepository.findById(id)
                .map(diary -> {
                    diary.setMealType(updatedDiary.getMealType());
                    diary.setFoodName(updatedDiary.getFoodName());
                    diary.setCalories(updatedDiary.getCalories());
                    return diaryRepository.save(diary);
                });
    }

    @Transactional
    public boolean deleteDiary(Long id) {
        if (diaryRepository.existsById(id)) {
            diaryRepository.deleteById(id);
            return true;
        }
        return false;
    }
}
