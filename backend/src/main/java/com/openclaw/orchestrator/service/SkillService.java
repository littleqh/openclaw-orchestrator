package com.openclaw.orchestrator.service;

import com.openclaw.orchestrator.dto.SkillRequest;
import com.openclaw.orchestrator.entity.Skill;
import com.openclaw.orchestrator.repository.SkillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SkillService {

    private final SkillRepository skillRepository;

    public List<Skill> listSkills() {
        return skillRepository.findAll();
    }

    public Skill getSkill(Long id) {
        return skillRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("NOT_FOUND:技能不存在"));
    }

    @Transactional
    public Skill createSkill(SkillRequest request) {
        if (skillRepository.existsByName(request.getName())) {
            throw new RuntimeException("CONFLICT:技能名称已存在");
        }
        Skill skill = Skill.builder()
                .name(request.getName())
                .description(request.getDescription())
                .build();
        return skillRepository.save(skill);
    }
}
