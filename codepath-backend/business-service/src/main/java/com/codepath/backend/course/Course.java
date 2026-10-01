package com.codepath.backend.course;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "course")
@Getter
@NoArgsConstructor
public class Course {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id")
    private Long ownerUserId;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String language;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private String status;

    public static Course create(Long ownerUserId, String code, String name, String language,
                                String description, String status) {
        Course course = new Course();
        course.ownerUserId = ownerUserId;
        course.code = code;
        course.name = name;
        course.language = language.toLowerCase();
        course.description = description;
        course.status = status;
        return course;
    }

    public void update(String name, String language, String description, String status) {
        this.name = name;
        this.language = language.toLowerCase();
        this.description = description;
        this.status = status;
    }
}
