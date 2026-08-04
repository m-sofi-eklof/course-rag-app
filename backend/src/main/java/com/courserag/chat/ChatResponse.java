package com.courserag.chat;

import java.util.List;
import java.util.UUID;

public record ChatResponse(String answer, List<UUID> sourceChunkIds) {}
