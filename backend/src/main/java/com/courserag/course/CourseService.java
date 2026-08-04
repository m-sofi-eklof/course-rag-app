package com.courserag.course;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseRepository courseRepository;
    private final S3Client s3Client;

    @Value("${r2.bucket-name}")
    private String bucket;

    public Course create(String name) {
        Course course = Course.builder().name(name).build();
        return courseRepository.save(course);
    }

    public List<Course> findAll() {
        return courseRepository.findAll();
    }

    public Course findById(UUID id) {
        return courseRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found: " + id));
    }

    /**
     * Deletes a course and all associated data.
     *
     * R2 cleanup is best-effort: if any batch delete fails, the failure is logged and
     * deletion continues. The course row (and all DB data) is always removed regardless.
     *
     * Rationale: S3-compatible APIs are not transactional. By the time a batch fails,
     * earlier batches have already run, so aborting the DB delete wouldn't restore a
     * consistent state — it would just leave the course visible in the UI while some
     * objects are already gone from R2. Orphaned R2 objects are inert once the DB rows
     * are removed (no app path can reach them) and can be cleaned up from the R2/MinIO
     * console using the logged keys if needed. A transient R2 error should not block
     * course deletion permanently.
     */
    public void delete(UUID courseId) {
        findById(courseId); // 404 if not found

        deleteR2Objects(courseId);

        // ON DELETE CASCADE in the schema propagates deletion to documents,
        // ingestion_jobs, and chunks automatically.
        courseRepository.deleteById(courseId);
    }

    /**
     * Lists all R2 objects under documents/{courseId}/ with pagination and
     * batch-deletes them (up to 1000 keys per DeleteObjects call, per S3 API limits).
     * Failures per batch are logged but do not abort the loop.
     */
    private void deleteR2Objects(UUID courseId) {
        String prefix = "documents/" + courseId + "/";
        String continuationToken = null;
        int totalDeleted = 0;
        int totalFailed = 0;

        do {
            ListObjectsV2Request.Builder listReq = ListObjectsV2Request.builder()
                    .bucket(bucket)
                    .prefix(prefix);
            if (continuationToken != null) {
                listReq.continuationToken(continuationToken);
            }

            ListObjectsV2Response listResp = s3Client.listObjectsV2(listReq.build());

            List<ObjectIdentifier> keys = listResp.contents().stream()
                    .map(obj -> ObjectIdentifier.builder().key(obj.key()).build())
                    .toList();

            if (!keys.isEmpty()) {
                DeleteObjectsRequest deleteReq = DeleteObjectsRequest.builder()
                        .bucket(bucket)
                        .delete(Delete.builder()
                                .objects(keys)
                                .quiet(false) // request per-object success/error detail
                                .build())
                        .build();

                DeleteObjectsResponse deleteResp = s3Client.deleteObjects(deleteReq);

                totalDeleted += deleteResp.deleted().size();

                List<S3Error> errors = deleteResp.errors();
                if (!errors.isEmpty()) {
                    totalFailed += errors.size();
                    List<String> failedKeys = new ArrayList<>();
                    for (S3Error err : errors) {
                        failedKeys.add(err.key());
                        log.error("Failed to delete R2 object: key={} code={} message={}",
                                err.key(), err.code(), err.message());
                    }
                    log.warn("Course {}: {} R2 object(s) could not be deleted and may need manual cleanup: {}",
                            courseId, failedKeys.size(), failedKeys);
                }
            }

            continuationToken = listResp.isTruncated() ? listResp.nextContinuationToken() : null;

        } while (continuationToken != null);

        if (totalFailed > 0) {
            log.warn("Course {} R2 cleanup complete: {} deleted, {} failed (proceeding with DB deletion)",
                    courseId, totalDeleted, totalFailed);
        } else {
            log.info("Course {} R2 cleanup complete: {} object(s) deleted", courseId, totalDeleted);
        }
    }
}
