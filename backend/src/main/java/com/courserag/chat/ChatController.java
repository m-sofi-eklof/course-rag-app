package com.courserag.chat;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/courses/{courseId}/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final ChatRateLimiter rateLimiter;

    @PostMapping
    public ChatResponse chat(
            @PathVariable UUID courseId,
            @Valid @RequestBody ChatRequest req,
            HttpServletRequest httpRequest) {

        String clientIp = resolveClientIp(httpRequest);
        if (!rateLimiter.isAllowed(clientIp)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Chat rate limit exceeded — please wait before sending another question");
        }

        return chatService.chat(courseId, req.question());
    }

    /**
     * Returns the direct TCP peer address.
     *
     * X-Forwarded-For is intentionally NOT trusted here: there is no reverse proxy
     * in front of this app yet, so the header is client-controlled and trivially
     * spoofable — an attacker could rotate it to bypass the rate limit entirely.
     * Switch to reading X-Forwarded-For (and validate it against a known proxy IP)
     * once a real reverse proxy (nginx, Caddy, etc.) is deployed in front.
     */
    private String resolveClientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
