package com.example.demo;

import org.springframework.stereotype.Component;

import java.util.*;

@Component
class KeywordCategoryClassifier implements CategoryClassifier {
    private static final Map<String, List<String>> KEYWORDS;

    static {
        Map<String, List<String>> keywords = new LinkedHashMap<>();
        // Specific sign-in and security issues are considered before general application errors.
        keywords.put("Authentication", List.of("authentication", "authenticate", "login", "log in", "sign in",
                "password", "credential", "mfa", "2fa", "sso", "single sign on", "locked out", "token"));
        keywords.put("Security", List.of("security", "malware", "virus", "ransomware", "phishing", "breach",
                "vulnerability", "suspicious", "encryption", "unauthorized", "data leak"));
        keywords.put("Database", List.of("database", "db", "sql", "query", "table", "transaction", "deadlock",
                "postgres", "postgresql", "mysql", "oracle", "mongodb"));
        keywords.put("Network", List.of("network", "wifi", "wi fi", "vpn", "internet", "dns", "latency",
                "connection", "router", "switch", "packet loss", "bandwidth"));
        keywords.put("Hardware", List.of("hardware", "laptop", "desktop", "monitor", "keyboard", "mouse",
                "printer", "hard drive", "disk", "battery", "power supply", "device", "workstation"));
        keywords.put("Cloud", List.of("cloud", "aws", "azure", "gcp", "kubernetes", "container", "instance",
                "bucket", "cloud storage", "virtual machine", "serverless"));
        keywords.put("Software", List.of("software", "install", "installation", "update", "upgrade", "license",
                "version", "package", "driver", "dependency", "patch"));
        keywords.put("Application", List.of("application", "app", "portal", "website", "web page", "page",
                "payment", "crash", "error", "unavailable", "screen", "form", "upload"));
        KEYWORDS = Collections.unmodifiableMap(keywords);
    }

    @Override
    public Optional<Category> classify(String description, List<Category> categories) {
        String text = " " + normalize(description) + " ";
        Category best = null;
        int bestHits = 0;
        for (Map.Entry<String, List<String>> mapping : KEYWORDS.entrySet()) {
            Category category = categories.stream()
                    .filter(candidate -> candidate.getName() != null
                            && candidate.getName().trim().equalsIgnoreCase(mapping.getKey()))
                    .findFirst().orElse(null);
            if (category == null) continue;

            int hits = 0;
            for (String keyword : mapping.getValue()) {
                String normalizedKeyword = normalize(keyword);
                if (!normalizedKeyword.isEmpty() && text.contains(" " + normalizedKeyword + " ")) hits++;
            }
            if (hits > bestHits) {
                bestHits = hits;
                best = category;
            }
        }
        return Optional.ofNullable(best);
    }

    private String normalize(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }
}
