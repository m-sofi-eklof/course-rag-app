package com.courserag.chunk;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/chunks")
@RequiredArgsConstructor
public class ChunkController {

    private final ChunkRepository chunkRepository;

    @GetMapping("/{id}")
    public Chunk get(@PathVariable UUID id) {
        return chunkRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chunk not found: " + id));
    }
}
