package com.openclaw.orchestrator.service;

import com.openclaw.orchestrator.dto.WorkerRequest;
import com.openclaw.orchestrator.entity.LLMModel;
import com.openclaw.orchestrator.entity.Skill;
import com.openclaw.orchestrator.entity.Worker;
import com.openclaw.orchestrator.repository.LLMModelRepository;
import com.openclaw.orchestrator.repository.SkillRepository;
import com.openclaw.orchestrator.repository.WorkerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class WorkerService {

    private final WorkerRepository workerRepository;
    private final SkillRepository skillRepository;
    private final LLMModelRepository llmModelRepository;

    public List<Worker> listWorkers() {
        return workerRepository.findAllByOrderByCreatedAtDesc();
    }

    public Worker getWorker(Long id) {
        return workerRepository.findByIdWithSkills(id)
                .orElseThrow(() -> new RuntimeException("NOT_FOUND:员工不存在"));
    }

    @Transactional
    public Worker createWorker(WorkerRequest request) {
        // Validate localRuntime requires modelId
        if (Boolean.TRUE.equals(request.getLocalRuntime()) && request.getModelId() == null) {
            throw new RuntimeException("本地运行模式必须选择模型");
        }

        Worker worker = Worker.builder()
                .name(request.getName())
                .nickname(request.getNickname())
                .role(request.getRole())
                .gatewayUrl(request.getGatewayUrl())
                .gatewayToken(request.getGatewayToken())
                .personality(request.getPersonality())
                .status(parseStatus(request.getStatus()))
                .avatar(request.getAvatar())
                .localRuntime(request.getLocalRuntime() != null ? request.getLocalRuntime() : false)
                .systemPrompt(request.getSystemPrompt())
                .skills(new HashSet<>())
                .build();

        if (request.getModelId() != null) {
            LLMModel model = llmModelRepository.findById(request.getModelId())
                    .orElseThrow(() -> new RuntimeException("NOT_FOUND:模型不存在"));
            worker.setModel(model);
        }

        worker = workerRepository.save(worker);
        updateWorkerSkills(worker, request.getSkillIds());
        return worker;
    }

    @Transactional
    public Worker updateWorker(Long id, WorkerRequest request) {
        Worker worker = workerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("NOT_FOUND:员工不存在"));

        // Validate localRuntime requires modelId
        if (Boolean.TRUE.equals(request.getLocalRuntime()) && request.getModelId() == null) {
            throw new RuntimeException("本地运行模式必须选择模型");
        }

        worker.setName(request.getName());
        worker.setNickname(request.getNickname());
        worker.setRole(request.getRole());
        worker.setGatewayUrl(request.getGatewayUrl());
        worker.setGatewayToken(request.getGatewayToken());
        worker.setPersonality(request.getPersonality());
        worker.setStatus(parseStatus(request.getStatus()));
        worker.setAvatar(request.getAvatar());
        worker.setLocalRuntime(request.getLocalRuntime() != null ? request.getLocalRuntime() : false);
        worker.setSystemPrompt(request.getSystemPrompt());

        if (request.getModelId() != null) {
            LLMModel model = llmModelRepository.findById(request.getModelId())
                    .orElseThrow(() -> new RuntimeException("NOT_FOUND:模型不存在"));
            worker.setModel(model);
        } else {
            worker.setModel(null);
        }

        worker = workerRepository.save(worker);
        updateWorkerSkills(worker, request.getSkillIds());
        return worker;
    }

    @Transactional
    public void deleteWorker(Long id) {
        if (!workerRepository.existsById(id)) {
            throw new RuntimeException("NOT_FOUND:员工不存在");
        }
        workerRepository.deleteById(id);
    }

    private void updateWorkerSkills(Worker worker, List<Long> skillIds) {
        if (skillIds == null || skillIds.isEmpty()) {
            worker.setSkills(new HashSet<>());
        } else {
            Set<Skill> skills = new HashSet<>(skillRepository.findAllById(skillIds));
            worker.setSkills(skills);
        }
    }

    private Worker.WorkerStatus parseStatus(String status) {
        if (status == null || status.isEmpty()) {
            return Worker.WorkerStatus.OFFLINE;
        }
        try {
            return Worker.WorkerStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Worker.WorkerStatus.OFFLINE;
        }
    }
}
