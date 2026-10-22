package com.neha.StreamHub.services;

import com.neha.StreamHub.dtos.response.CategoryDto;

import java.util.List;
import java.util.Map;

public interface CategoryService {

    List<CategoryDto> getAllCategories();

    CategoryDto createCategory(String name, String description);

    CategoryDto updateCategory(String id, String name, String description);

    Map<String, Object> deleteCategory(String id);
}
