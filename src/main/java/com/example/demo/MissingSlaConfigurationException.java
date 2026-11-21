package com.example.demo;

public class MissingSlaConfigurationException extends RuntimeException {
    public MissingSlaConfigurationException(Priority p) { super("No SLA policy configured for priority " + p); }
}
