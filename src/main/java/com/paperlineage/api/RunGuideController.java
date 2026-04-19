package com.paperlineage.api;

import com.paperlineage.ingestion.RunGuide;
import com.paperlineage.ingestion.RunGuideService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class RunGuideController {

    private static final Logger log = LoggerFactory.getLogger(RunGuideController.class);

    private final RunGuideService runGuideService;

    public RunGuideController(RunGuideService runGuideService) {
        this.runGuideService = runGuideService;
    }

    @GetMapping("/run-guide")
    public ApiResponse<RunGuide> getRunGuide(
            @RequestParam String repoName,
            @RequestParam(defaultValue = "") String repoUrl,
            @RequestParam(defaultValue = "false") boolean hasDocker,
            @RequestParam(defaultValue = "false") boolean hasCi,
            @RequestParam(defaultValue = "false") boolean hasDeps,
            @RequestParam(defaultValue = "0") int score,
            @RequestParam(defaultValue = "") String paperTitle
    ) {
        try {
            RunGuide guide = runGuideService.generate(
                    repoName, repoUrl, hasDocker, hasCi, hasDeps, score, paperTitle);
            return new ApiResponse<>(guide, null, null);
        } catch (Exception e) {
            log.error("Run guide failed for {}: {}", repoName, e.getMessage());
            return new ApiResponse<>(null, e.getMessage(), null);
        }
    }
}
