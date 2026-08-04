-- Re-add foreign keys with ON DELETE CASCADE so deleting a course automatically
-- removes its documents, and deleting a document removes its ingestion jobs and chunks.
-- This lets CourseService.delete() do a single courseRepository.deleteById() after
-- R2 cleanup rather than manually orchestrating deletion order in application code.

-- documents → courses
ALTER TABLE documents
    DROP CONSTRAINT documents_course_id_fkey,
    ADD  CONSTRAINT documents_course_id_fkey
         FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE;

-- ingestion_jobs → documents
ALTER TABLE ingestion_jobs
    DROP CONSTRAINT ingestion_jobs_document_id_fkey,
    ADD  CONSTRAINT ingestion_jobs_document_id_fkey
         FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE;

-- chunks → documents
ALTER TABLE chunks
    DROP CONSTRAINT chunks_document_id_fkey,
    ADD  CONSTRAINT chunks_document_id_fkey
         FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE;

-- chunks → courses (redundant FK; cascade here too for consistency)
ALTER TABLE chunks
    DROP CONSTRAINT chunks_course_id_fkey,
    ADD  CONSTRAINT chunks_course_id_fkey
         FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE;
