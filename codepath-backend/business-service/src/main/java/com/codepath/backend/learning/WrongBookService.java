package com.codepath.backend.learning;

import com.codepath.backend.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class WrongBookService {
    private static final Set<String> STATUSES = Set.of("OPEN", "REVIEWING", "RESOLVED");
    private final WrongBookRepository repository;

    public WrongBookService(WrongBookRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void applyDiagnosis(Long userId, Long problemId, Long submissionId, String errorType) {
        var existing = repository.findByUserIdAndProblemId(userId, problemId);
        if ("NONE".equals(errorType)) {
            existing.ifPresent(WrongBook::resolve);
            return;
        }
        WrongBook item = existing.orElseGet(() -> WrongBook.create(userId, problemId, submissionId, errorType));
        if (item.getId() != null) {
            item.record(submissionId, errorType);
        }
        repository.save(item);
    }

    @Transactional(readOnly = true)
    public List<WrongBook> list(Long userId, String status) {
        return status == null ? repository.findByUserIdOrderByLastMistakeAtDesc(userId)
                : repository.findByUserIdAndStatusOrderByLastMistakeAtDesc(userId, status.toUpperCase());
    }

    @Transactional
    public WrongBook update(Long userId, Long id, String status, String notes) {
        if (!STATUSES.contains(status.toUpperCase())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "无效的错题状态");
        }
        WrongBook item = repository.findById(id)
                .filter(value -> value.getUserId().equals(userId))
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "错题记录不存在"));
        item.update(status.toUpperCase(), notes);
        return item;
    }
}
