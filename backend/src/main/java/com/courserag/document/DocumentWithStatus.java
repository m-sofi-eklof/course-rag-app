package com.courserag.document;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DocumentWithStatus(
        UUID id,
        UUID courseId,
        String filename,
        String type,
        String storageKey,
        String status,
        OffsetDateTime createdAt,
        String jobStatus
) {}
