package com.neha.StreamHub.specifications;

import com.neha.StreamHub.entities.Video;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.criteria.Predicate;

public class VideoSpecification {

    public static Specification<Video> build(
            String search,
            UUID userId,
            Instant createdAfter,
            Instant createdBefore,
            String contentType,
            String category
    ) {
        return (root, query, cb) -> {

            // Create a list to hold the predicates
            List<Predicate> predicates = new ArrayList<>();

            if (search != null && !search.isEmpty()) {
               // SECURITY: search ko seedha LIKE pattern me daalne se "%" ya "_"
               // wildcard ban jata tha — search "%" poori table match kar deta tha
               // (full scan + catalogue dump). Ab wildcard characters escape karte hain.
               String like = "%" + escapeLikePattern(search.toLowerCase()) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), like, '\\'),
                        cb.like(cb.lower(root.get("description")), like, '\\')));
            }

            // Filter by User
            if (userId != null) {
                predicates.add(
                        cb.equal(
                                root.get("user").get("id"),
                                userId
                        )
                );
            }

            // Filter by Content Type
            if (contentType != null && !contentType.isBlank()) {
                predicates.add(
                        cb.equal(
                                root.get("contentType"),
                                contentType
                        )
                );
            }

            // Filter by Category (slug — e.g. "tech", "music")
            if (category != null && !category.isBlank()) {
                predicates.add(
                        cb.equal(
                                cb.lower(root.get("category")),
                                category.toLowerCase()
                        )
                );
            }

            // Filter by Created After
            if (createdAfter != null) {
                predicates.add(
                        cb.greaterThanOrEqualTo(
                                root.get("createdAt"),
                                createdAfter
                        )
                );
            }

            // Filter by Created Before
            if (createdBefore != null) {
                predicates.add(
                        cb.lessThanOrEqualTo(
                                root.get("createdAt"),
                                createdBefore
                        )
                );
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    // LIKE pattern me wildcard characters (%, _, \) escape karo
    private static String escapeLikePattern(String value) {
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
