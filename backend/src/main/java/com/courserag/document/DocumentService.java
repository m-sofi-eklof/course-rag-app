package com.courserag.document;

import com.courserag.course.CourseService;
import com.courserag.ingestion.IngestionJob;
import com.courserag.ingestion.IngestionJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final IngestionJobRepository ingestionJobRepository;
    private final CourseService courseService;
    private final S3Client s3Client;

    @Value("${r2.bucket-name}")
    private String bucket;

    @Transactional
    public Document upload(UUID courseId, MultipartFile file) throws IOException {
        // Validates course exists — throws 404 if not
        courseService.findById(courseId);

        validatePdf(file);

        String safeFilename = sanitizeFilename(file.getOriginalFilename());
        String storageKey = "documents/" + courseId + "/" + UUID.randomUUID() + "/" + safeFilename;

        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(storageKey)
                        .contentType("application/pdf")
                        .contentLength(file.getSize())
                        .build(),
                RequestBody.fromInputStream(file.getInputStream(), file.getSize())
        );

        Document document = Document.builder()
                .courseId(courseId)
                .filename(safeFilename)
                .storageKey(storageKey)
                .build();
        document = documentRepository.save(document);

        IngestionJob job = IngestionJob.builder()
                .documentId(document.getId())
                .build();
        ingestionJobRepository.save(job);

        return document;
    }

    public List<DocumentWithStatus> listForCourse(UUID courseId) {
        courseService.findById(courseId);

        return documentRepository.findByCourseIdOrderByCreatedAtDesc(courseId).stream()
                .map(doc -> {
                    String jobStatus = ingestionJobRepository
                            .findFirstByDocumentIdOrderByCreatedAtDesc(doc.getId())
                            .map(IngestionJob::getStatus)
                            .orElse(null);
                    return new DocumentWithStatus(
                            doc.getId(),
                            doc.getCourseId(),
                            doc.getFilename(),
                            doc.getType(),
                            doc.getStorageKey(),
                            doc.getStatus(),
                            doc.getCreatedAt(),
                            jobStatus
                    );
                })
                .toList();
    }

    private void validatePdf(MultipartFile file) {
        String contentType = file.getContentType();
        if (!"application/pdf".equalsIgnoreCase(contentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only PDF files are accepted. Received content type: " + contentType);
        }
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase().endsWith(".pdf")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "File must have a .pdf extension");
        }
    }

    /**
     * Strips path separators and replaces anything outside [A-Za-z0-9._-] with underscores.
     * Guarantees the result is safe for use as a storage key segment.
     */
    private String sanitizeFilename(String raw) {
        if (raw == null || raw.isBlank()) {
            return "upload.pdf";
        }
        // Strip any leading path components (e.g. "../../evil.pdf" → "evil.pdf")
        String name = raw.replaceAll(".*/", "").replaceAll(".*\\\\", "");
        // Replace unsafe characters
        name = name.replaceAll("[^A-Za-z0-9._\\-]", "_");
        if (name.isBlank() || name.equals(".pdf")) {
            return "upload.pdf";
        }
        return name;
    }
}
