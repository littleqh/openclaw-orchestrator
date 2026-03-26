package com.openclaw.orchestrator.controller;

import com.openclaw.orchestrator.dto.SkillDeleteResponse;
import com.openclaw.orchestrator.dto.SkillDetail;
import com.openclaw.orchestrator.dto.SkillRequest;
import com.openclaw.orchestrator.service.SkillService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/skills")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class SkillController {

    private final SkillService skillService;

    @GetMapping
    public List<SkillDetail> list() {
        return skillService.list();
    }

    @GetMapping("/{id}")
    public SkillDetail get(@PathVariable Long id) {
        return skillService.getById(id);
    }

    @PostMapping
    public SkillDetail create(@Valid @RequestBody SkillRequest request) {
        return skillService.create(request);
    }

    @PutMapping("/{id}")
    public SkillDetail update(@PathVariable Long id, @Valid @RequestBody SkillRequest request) {
        return skillService.update(id, request);
    }

    @GetMapping("/{id}/check-delete")
    public SkillDeleteResponse checkDelete(@PathVariable Long id) {
        return skillService.checkDelete(id);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        skillService.delete(id);
    }

    @DeleteMapping("/{id}/force")
    public void forceDelete(@PathVariable Long id) {
        skillService.forceDelete(id);
    }
}
