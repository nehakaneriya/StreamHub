package com.neha.StreamHub.services.impl;

import com.neha.StreamHub.cache.VideoCacheService;
import com.neha.StreamHub.dtos.response.CategoryDto;
import com.neha.StreamHub.entities.Category;
import com.neha.StreamHub.exception.BadRequestException;
import com.neha.StreamHub.exception.DuplicateResourceException;
import com.neha.StreamHub.exception.ResourceNotFoundException;
import com.neha.StreamHub.repositories.CategoryRepository;
import com.neha.StreamHub.repositories.VideoRepository;
import com.neha.StreamHub.services.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    // "Other" special category hai — delete nahi ho sakti, aur videos yahin move hoti hain
    public static final String DEFAULT_CATEGORY_SLUG = "other";

    private final CategoryRepository categoryRepository;
    private final VideoRepository videoRepository;
    private final VideoCacheService videoCacheService;

    @Override
    public List<CategoryDto> getAllCategories() {
        return categoryRepository.findAllByOrderByNameAsc()
                .stream()
                .map(this::mapToDto)
                .toList();
    }

    @Override
    @Transactional
    public CategoryDto createCategory(String name, String description) {

        if (!StringUtils.hasText(name)) {
            throw new BadRequestException("Category name is required");
        }

        String slug = toSlug(name);

        // 'other' reserved hai — admin wo category na bana paye
        if (DEFAULT_CATEGORY_SLUG.equalsIgnoreCase(slug)) {
            throw new BadRequestException("'Other' is a reserved category");
        }

        if (categoryRepository.existsBySlug(slug)) {
            throw new DuplicateResourceException("Category '" + name + "' already exists");
        }

        Category category = Category.builder()
                .name(name.trim())
                .slug(slug)
                .description(description)
                .build();

        Category saved = categoryRepository.save(category);

        // Nayi category aayi — feed cache ko refresh karo (filter chips pe asar)
        videoCacheService.evictScrollCacheAfterCommit();

        return mapToDto(saved);
    }

    @Override
    @Transactional
    public CategoryDto updateCategory(String id, String name, String description) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        if (!StringUtils.hasText(name)) {
            throw new BadRequestException("Category name is required");
        }

        String oldSlug = category.getSlug();
        String newSlug = toSlug(name);

        // If name changed
        if (!category.getName().trim().equalsIgnoreCase(name.trim())) {
            // 'other' reserved category hai — iska naam modify nahi ho sakta
            if (DEFAULT_CATEGORY_SLUG.equalsIgnoreCase(oldSlug)) {
                throw new BadRequestException("The 'Other' category name cannot be modified");
            }
            if (DEFAULT_CATEGORY_SLUG.equalsIgnoreCase(newSlug)) {
                throw new BadRequestException("'Other' is a reserved category");
            }

            if (!newSlug.equalsIgnoreCase(oldSlug) && categoryRepository.existsBySlug(newSlug)) {
                throw new DuplicateResourceException("Category '" + name + "' already exists");
            }

            category.setName(name.trim());
            category.setSlug(newSlug);

            // Existing videos ki category ko naye slug par migrate karo
            if (!oldSlug.equalsIgnoreCase(newSlug)) {
                videoRepository.moveCategory(oldSlug, newSlug);
            }
        }

        category.setDescription(description != null ? description.trim() : null);

        Category updated = categoryRepository.save(category);

        // Feed cache clear karo taaki updated category filter chips pe reflect ho
        videoCacheService.evictScrollCacheAfterCommit();

        return mapToDto(updated);
    }

    @Override
    @Transactional
    public Map<String, Object> deleteCategory(String id) {

        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        if (DEFAULT_CATEGORY_SLUG.equalsIgnoreCase(category.getSlug())) {
            throw new BadRequestException("The 'Other' category cannot be deleted");
        }

        // Option B: is category ke saare videos 'other' me move karo
        int moved = videoRepository.moveCategory(category.getSlug(), DEFAULT_CATEGORY_SLUG);

        categoryRepository.delete(category);

        // Videos ki category badli — cached feed purani ho gayi
        videoCacheService.evictScrollCacheAfterCommit();

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Category deleted. " + moved + " video(s) moved to 'Other'.");
        response.put("movedVideos", moved);
        return response;
    }

    // "Web Development!" -> "web-development"
    private String toSlug(String name) {
        String slug = name.toLowerCase(Locale.ROOT)
                .trim()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return slug.isEmpty() ? "category" : slug;
    }

    private CategoryDto mapToDto(Category category) {
        return CategoryDto.builder()
                .id(category.getId())
                .name(category.getName())
                .slug(category.getSlug())
                .description(category.getDescription())
                .build();
    }
}
