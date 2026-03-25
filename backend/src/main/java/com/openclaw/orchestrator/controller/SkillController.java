package com.openclaw.orchestrator.controller;

import com.openclaw.orchestrator.dto.SkillRequest;
import com.openclaw.orchestrator.entity.Skill;
import com.openclaw.orchestrator.service.SkillService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/skills")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class SkillController {

    private final SkillService skillService;

    @GetMapping
    public ResponseEntity<List<Skill>> list() {
        return ResponseEntity.ok(skillService.listSkills());
    }

    @PostMapping
    public ResponseEntity<Skill> create(@Valid @RequestBody SkillRequest request) {
        return ResponseEntity.ok(skillService.createSkill(request));
    }
}
