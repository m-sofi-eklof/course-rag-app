package com.courserag.chunk;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "chunks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Chunk {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    // embedding is intentionally not mapped — the Python worker writes it,
    // Java only needs to pass it as a query parameter string for similarity search.

    @Column(name = "source_type", nullable = false)
    private String sourceType;

    @Column(name = "page_number")
    private Integer pageNumber;

    @Column(name = "timestamp_seconds")
    private Integer timestampSeconds;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
