package com.codepath.backend.learning;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface WrongBookRepository extends JpaRepository<WrongBook, Long> {
    Optional<WrongBook> findByUserIdAndProblemId(Long userId, Long problemId);
    List<WrongBook> findByUserIdOrderByLastMistakeAtDesc(Long userId);
    List<WrongBook> findByUserIdAndStatusOrderByLastMistakeAtDesc(Long userId, String status);
}
