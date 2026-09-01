package org.sqahub.backend.controller;

import lombok.RequiredArgsConstructor;
import org.sqahub.backend.dto.GlobalSearchResponse;
import org.sqahub.backend.security.SecurityUtil;
import org.sqahub.backend.service.SearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Global Search lintas-entitas (Project/Feature/Test Case/Test Suite Run). Endpoint: /api/v1/search
 */
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;
    private final SecurityUtil securityUtil;

    // Path: GET /api/v1/search?q=...
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<GlobalSearchResponse> search(@RequestParam(name = "q", defaultValue = "") String query) {
        Long currentUserId = securityUtil.getAuthenticatedUserId();
        return ResponseEntity.ok(searchService.search(query, currentUserId));
    }
}
