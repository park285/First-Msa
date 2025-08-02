package com.dietdiary.diary.service;

import com.dietdiary.diary.domain.FreestyleDiary;
import com.dietdiary.diary.repository.FreestyleDiaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class FreestyleDiaryService {

    private final FreestyleDiaryRepository repository;

    public FreestyleDiary saveDiary(Long userId, LocalDate date, String text) {
        FreestyleDiary diary = repository.findByUserIdAndDate(userId, date)
                .orElse(new FreestyleDiary());

        diary.setUserId(userId);
        diary.setDate(date);
        diary.setText(text);

        return repository.save(diary);
    }

    @Transactional(readOnly = true)
    public Optional<FreestyleDiary> getDiary(Long userId, LocalDate date) {
        return repository.findByUserIdAndDate(userId, date);
    }
}
