package com.example.demo;

import org.springframework.stereotype.Component;
import java.util.*;

@Component
class KeywordCategoryClassifier implements CategoryClassifier {
    private static final Map<String, List<String>> KEYWORDS = Map.of(
            "Network",     List.of("network", "wifi", "vpn", "internet", "dns", "latency", "connection", "router"),
            "Database",    List.of("database", "db", "sql", "query", "table", "transaction", "deadlock"),
            "Application", List.of("app", "application", "payment", "login", "crash", "error", "page", "unavailable"));

    @Override
    public Optional<Category> classify(String description, List<Category> categories) {
        String text = description.toLowerCase();
        Category best = null;
        int bestHits = 0;
        for (Category c : categories) {
            int hits = 0;
            for (String kw : KEYWORDS.getOrDefault(c.getName(), List.of()))
                if (text.contains(kw)) hits++;
            if (hits > bestHits) { bestHits = hits; best = c; }
        }
        return Optional.ofNullable(best);
    }
}
