package com.neha.StreamHub.entities;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "categories",
        indexes = {
                @Index(name = "idx_category_slug", columnList = "slug")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Category extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 100)
    private String slug;

    @Column(length = 500)
    private String description;
}
