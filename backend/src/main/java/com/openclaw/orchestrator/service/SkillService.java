package com.openclaw.orchestrator.service;

import com.openclaw.orchestrator.dto.SkillDeleteResponse;
import com.openclaw.orchestrator.dto.SkillDetail;
import com.openclaw.orchestrator.dto.SkillRequest;
import com.openclaw.orchestrator.entity.Operation;
import com.openclaw.orchestrator.entity.Skill;
import com.openclaw.orchestrator.repository.OperationRepository;
import com.openclaw.orchestrator.repository.SkillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SkillService {

    private final SkillRepository skillRepository;
    private final OperationRepository operationRepository;

    public List<SkillDetail> list() {
        return skillRepository.findAll().stream()
            .map(this::toDetail)
            .collect(Collectors.toList());
    }

    public SkillDetail getById(Long id) {
        return skillRepository.findById(id)
            .map(this::toDetail)
            .orElseThrow(() -> new RuntimeException("NOT_FOUND:技能不存在"));
    }

    @Transactional
    public SkillDetail create(SkillRequest request) {
        if (skillRepository.existsByName(request.getName())) {
            throw new RuntimeException("CONFLICT:技能名称已存在");
        }
        Skill skill = Skill.builder()
            .name(request.getName())
            .description(request.getDescription())
            .build();
        return toDetail(skillRepository.save(skill));
    }

    @Transactional
    public SkillDetail update(Long id, SkillRequest request) {
        Skill skill = skillRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("NOT_FOUND:技能不存在"));
        skill.setName(request.getName());
        skill.setDescription(request.getDescription());
        return toDetail(skillRepository.save(skill));
    }

    @Transactional
    public void delete(Long id) {
        if (!skillRepository.existsById(id)) {
            throw new RuntimeException("NOT_FOUND:技能不存在");
        }
        skillRepository.deleteById(id);
    }

    public SkillDeleteResponse checkDelete(Long id) {
        if (!skillRepository.existsById(id)) {
            throw new RuntimeException("NOT_FOUND:技能不存在");
        }
        List<Operation> referencing = operationRepository.findBySkillId(id);
        return SkillDeleteResponse.builder()
            .referenced(!referencing.isEmpty())
            .referencedBy(referencing.stream()
                .map(o -> SkillDeleteResponse.OperationRef.builder()
                    .id(o.getId())
                    .name(o.getName())
                    .build())
                .collect(Collectors.toList()))
            .build();
    }

    @Transactional
    public void forceDelete(Long id) {
        if (!skillRepository.existsById(id)) {
            throw new RuntimeException("NOT_FOUND:技能不存在");
        }
        List<Operation> referencing = operationRepository.findBySkillId(id);
        for (Operation op : referencing) {
            op.getSkills().removeIf(s -> s.getId().equals(id));
            operationRepository.save(op);
        }
        skillRepository.deleteById(id);
    }

    private SkillDetail toDetail(Skill s) {
        return SkillDetail.builder()
            .id(s.getId())
            .name(s.getName())
            .description(s.getDescription())
            .createdAt(s.getCreatedAt())
            .updatedAt(s.getUpdatedAt())
            .build();
    }
}
