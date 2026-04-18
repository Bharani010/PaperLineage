package com.paperlineage.api;

import com.paperlineage.ingestion.IngestionResult;
import com.paperlineage.ingestion.IngestionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class IngestionController {

    private final IngestionService ingestionService;

    public IngestionController(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping("/ingest")
    public ResponseEntity<ApiResponse<IngestionResult>> ingest(
            @RequestParam String arxivId) {
        if (arxivId == null || arxivId.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(null, "arxivId is required", null));
        }
        IngestionResult result = ingestionService.ingest(arxivId.trim());
        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
