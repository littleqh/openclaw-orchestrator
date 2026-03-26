package com.openclaw.orchestrator.service;

import com.openclaw.orchestrator.dto.*;
import com.openclaw.orchestrator.entity.Operation;
import com.openclaw.orchestrator.entity.Skill;
import com.openclaw.orchestrator.repository.OperationRepository;
import com.openclaw.orchestrator.repository.SkillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OperationService {

    private final OperationRepository operationRepository;
    private final SkillRepository skillRepository;

    public List<OperationDetail> list() {
        return operationRepository.findAll().stream()
            .map(this::toDetailBasic)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public OperationDetail getById(Long id) {
        return operationRepository.findByIdWithSkills(id)
            .map(this::toDetail)
            .orElseThrow(() -> new RuntimeException("NOT_FOUND:操作不存在"));
    }

    @Transactional
    public OperationDetail create(OperationRequest request) {
        if (operationRepository.existsByName(request.getName())) {
            throw new RuntimeException("CONFLICT:操作名称已存在");
        }
        Operation op = Operation.builder()
            .name(request.getName())
            .description(request.getDescription())
            .skills(new HashSet<>())
            .build();
        if (request.getSkillIds() != null) {
            Set<Skill> skills = new HashSet<>(skillRepository.findAllById(request.getSkillIds()));
            op.setSkills(skills);
        }
        return toDetail(operationRepository.save(op));
    }

    @Transactional
    public OperationDetail update(Long id, OperationRequest request) {
        Operation op = operationRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("NOT_FOUND:操作不存在"));
        op.setName(request.getName());
        op.setDescription(request.getDescription());
        if (request.getSkillIds() != null) {
            Set<Skill> skills = new HashSet<>(skillRepository.findAllById(request.getSkillIds()));
            op.setSkills(skills);
        } else {
            op.setSkills(new HashSet<>());
        }
        return toDetail(operationRepository.save(op));
    }

    @Transactional
    public void delete(Long id) {
        if (!operationRepository.existsById(id)) {
            throw new RuntimeException("NOT_FOUND:操作不存在");
        }
        operationRepository.deleteById(id);
    }

    private OperationDetail toDetailBasic(Operation o) {
        return OperationDetail.builder()
            .id(o.getId())
            .name(o.getName())
            .description(o.getDescription())
            .createdAt(o.getCreatedAt())
            .updatedAt(o.getUpdatedAt())
            .build();
    }

    private OperationDetail toDetail(Operation o) {
        List<SkillDetail> skills = o.getSkills().stream()
            .map(s -> SkillDetail.builder()
                .id(s.getId())
                .name(s.getName())
                .description(s.getDescription())
                .build())
            .collect(Collectors.toList());
        return OperationDetail.builder()
            .id(o.getId())
            .name(o.getName())
            .description(o.getDescription())
            .skills(skills)
            .createdAt(o.getCreatedAt())
            .updatedAt(o.getUpdatedAt())
            .build();
    }
}
