package com.example.demo;

public record PriorityResult(Priority priority, int score, int baseScore, int affectedUsers, int affectedUsersBoost) {
    public PriorityResult(Priority priority, int score) {
        this(priority, score, score, 1, 0);
    }

    public String breakdown() {
        return "Base score " + baseScore + " + " + affectedUsersBoost + " affected-user boost ("
                + affectedUsers + " users) = " + score;
    }
}
