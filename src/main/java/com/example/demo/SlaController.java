package com.example.demo;

import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/admin")
class SlaController {
    private final SlaService sla;
    SlaController(SlaService sla) { this.sla = sla; }

    @GetMapping("/sla-policies") List<SlaPolicy> policies() { return sla.allPolicies(); }

    @PutMapping("/sla-policies/{id}")
    SlaPolicy update(@PathVariable Long id, @RequestParam int response, @RequestParam int resolution) {
        return sla.updatePolicy(id, response, resolution);
    }

    @PostMapping("/sla/check") Map<String, Integer> check() { return Map.of("alertsRaised", sla.check()); }
    @GetMapping("/sla/violations") List<Incident> violations() { return sla.violations(); }
}
