package com.example.demo;

import org.springframework.stereotype.Component;

public interface PriorityStrategy {
    PriorityResult calculate(int impact, int urgency, int businessCriticality);
}


@Component
class ScoreBasedPriorityStrategy implements PriorityStrategy {
    @Override
    public PriorityResult calculate(int impact, int urgency, int businessCriticality) {
        int score = impact * urgency * businessCriticality;   // range 1 to 125
        Priority p = score >= 60 ? Priority.P1
                : score >= 30 ? Priority.P2
                : score >= 12 ? Priority.P3
                : Priority.P4;
        return new PriorityResult(p, score);
    }
}
