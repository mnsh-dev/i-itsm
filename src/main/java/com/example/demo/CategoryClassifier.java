package com.example.demo;

import java.util.*;


public interface CategoryClassifier {
    Optional<Category> classify(String description, List<Category> categories);
}
