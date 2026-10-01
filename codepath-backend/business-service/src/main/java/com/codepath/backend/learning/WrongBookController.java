package com.codepath.backend.learning;

import com.codepath.backend.auth.AppPrincipal;
import com.codepath.backend.common.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/wrong-book")
public class WrongBookController {
    private final WrongBookService service;

    public WrongBookController(WrongBookService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<WrongBook>> list(@AuthenticationPrincipal AppPrincipal principal,
                                             @RequestParam(required = false) String status) {
        return ApiResponse.ok(service.list(principal.userId(), status));
    }

    @PutMapping("/{id}")
    public ApiResponse<WrongBook> update(@AuthenticationPrincipal AppPrincipal principal,
                                         @PathVariable Long id,
                                         @RequestBody UpdateRequest request) {
        return ApiResponse.ok(service.update(principal.userId(), id, request.status(), request.notes()));
    }

    public record UpdateRequest(String status, String notes) {}
}
