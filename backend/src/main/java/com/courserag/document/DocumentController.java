package com.courserag.document;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/courses/{courseId}/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Document upload(
            @PathVariable UUID courseId,
            @RequestParam("file") MultipartFile file) throws IOException {
        return documentService.upload(courseId, file);
    }

    @GetMapping
    public List<DocumentWithStatus> list(@PathVariable UUID courseId) {
        return documentService.listForCourse(courseId);
    }
}
