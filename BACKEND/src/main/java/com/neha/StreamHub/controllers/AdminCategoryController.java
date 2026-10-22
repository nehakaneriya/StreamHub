package com.neha.StreamHub.controllers;

import com.neha.StreamHub.dtos.request.CategoryRequest;
import com.neha.StreamHub.dtos.response.CategoryDto;
import com.neha.StreamHub.dtos.response.CategoryStatsDto;
import com.neha.StreamHub.services.AdminService;
import com.neha.StreamHub.services.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/categories")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminCategoryController {

    private final CategoryService categoryService;
    private final AdminService adminService;

    // Admin: nayi category add karo
    @PostMapping
    public ResponseEntity<CategoryDto> createCategory(@RequestBody CategoryRequest request) {
        CategoryDto created = categoryService.createCategory(request.getName(), request.getDescription());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // Admin: category edit / update karo
    @PutMapping("/{id}")
    public ResponseEntity<CategoryDto> updateCategory(
            @PathVariable String id,
            @RequestBody CategoryRequest request
    ) {
        CategoryDto updated = categoryService.updateCategory(id, request.getName(), request.getDescription());
        return ResponseEntity.ok(updated);
    }

    // Admin: category delete karo — uske videos 'other' me move ho jayenge
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteCategory(@PathVariable String id) {
        return ResponseEntity.ok(categoryService.deleteCategory(id));
    }

    // Admin: har category me kitne videos hain
    @GetMapping("/stats")
    public ResponseEntity<List<CategoryStatsDto>> getCategoryStats() {
        return ResponseEntity.ok(adminService.getCategoryStats());
    }
}
