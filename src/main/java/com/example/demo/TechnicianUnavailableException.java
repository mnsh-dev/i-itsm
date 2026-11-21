package com.example.demo;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

public class TechnicianUnavailableException extends RuntimeException {
    public TechnicianUnavailableException(String message) { super(message); }
}

