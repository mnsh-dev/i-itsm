package com.example.demo;

import org.springframework.stereotype.Component;

public interface PriorityStrategy {
    PriorityResult calculate(int impact, int urgency, int businessCriticality);

    default PriorityResult calculate(int impact, int urgency, int businessCriticality, int affectedUsers) {
        return calculate(impact, urgency, businessCriticality);
    }
}


@Component
class ScoreBasedPriorityStrategy implements PriorityStrategy {
    @Override
    public PriorityResult calculate(int impact, int urgency, int businessCriticality) {
        return calculate(impact, urgency, businessCriticality, 1);
    }

    @Override
    public PriorityResult calculate(int impact, int urgency, int businessCriticality, int affectedUsers) {
        int users = Math.max(1, affectedUsers);
        int baseScore = impact * urgency * businessCriticality;
        int userBoost = users == 1 ? 0 : users <= 10 ? 10 : users <= 50 ? 20 : 35;
        int score = baseScore + userBoost;
        Priority p = score >= 60 ? Priority.P1
                : score >= 30 ? Priority.P2
                : score >= 12 ? Priority.P3
                : Priority.P4;
        return new PriorityResult(p, score, baseScore, users, userBoost);
    }
}
