package com.courserag.chunk;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChunkRepository extends JpaRepository<Chunk, UUID> {

    /**
     * Cosine similarity search over pgvector embeddings for a given course.
     * The embedding is passed as a pre-formatted pgvector literal string, e.g. "[0.1,0.2,...]",
     * and cast to the vector type in the query so Hibernate doesn't need to know the type.
     */
    @Query(value = """
            SELECT id, document_id, course_id, content, source_type, page_number,
                   timestamp_seconds, created_at
            FROM chunks
            WHERE course_id = :courseId
              AND embedding IS NOT NULL
            ORDER BY embedding <=> CAST(:embeddingStr AS vector)
            LIMIT :k
            """, nativeQuery = true)
    List<Chunk> findTopKSimilar(
            @Param("courseId") UUID courseId,
            @Param("embeddingStr") String embeddingStr,
            @Param("k") int k);

    Optional<Chunk> findByIdAndCourseId(UUID id, UUID courseId);
}
