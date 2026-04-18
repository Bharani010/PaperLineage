package com.paperlineage.api;

import com.paperlineage.chat.HybridQueryEngine;
import com.paperlineage.chat.QueryResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class QueryController {

    private final HybridQueryEngine queryEngine;

    public QueryController(HybridQueryEngine queryEngine) {
        this.queryEngine = queryEngine;
    }

    @GetMapping("/api/query")
    public ApiResponse<QueryResult> query(@RequestParam String q) {
        if (q == null || q.isBlank()) {
            return ApiResponse.error("Query parameter 'q' is required");
        }
        QueryResult result = queryEngine.query(q);
        return ApiResponse.ok(result);
    }
}
