package com.openclaw.orchestrator.service;

import com.openclaw.orchestrator.dto.WorkerRequest;
import com.openclaw.orchestrator.entity.Skill;
import com.openclaw.orchestrator.entity.Worker;
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

    public List<Worker> listWorkers() {
        return workerRepository.findAllByOrderByCreatedAtDesc();
    }

    public Worker getWorker(Long id) {
        return workerRepository.findByIdWithSkills(id)
                .orElseThrow(() -> new RuntimeException("NOT_FOUND:员工不存在"));
    }

    @Transactional
    public Worker createWorker(WorkerRequest request) {
        Worker worker = Worker.builder()
                .name(request.getName())
                .nickname(request.getNickname())
                .role(request.getRole())
                .gatewayUrl(request.getGatewayUrl())
                .gatewayToken(request.getGatewayToken())
                .personality(request.getPersonality())
                .status(parseStatus(request.getStatus()))
                .avatar(request.getAvatar())
                .skills(new HashSet<>())
                .build();
        worker = workerRepository.save(worker);
        updateWorkerSkills(worker, request.getSkillIds());
        return worker;
    }

    @Transactional
    public Worker updateWorker(Long id, WorkerRequest request) {
        Worker worker = workerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("NOT_FOUND:员工不存在"));
        worker.setName(request.getName());
        worker.setNickname(request.getNickname());
        worker.setRole(request.getRole());
        worker.setGatewayUrl(request.getGatewayUrl());
        worker.setGatewayToken(request.getGatewayToken());
        worker.setPersonality(request.getPersonality());
        worker.setStatus(parseStatus(request.getStatus()));
        worker.setAvatar(request.getAvatar());
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
