package com.openclaw.orchestrator.service;

import com.openclaw.orchestrator.dto.*;
import com.openclaw.orchestrator.entity.*;
import com.openclaw.orchestrator.repository.OperationRepository;
import com.openclaw.orchestrator.repository.WorkflowRepository;
import com.openclaw.orchestrator.repository.WorkerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkflowService {

    private final WorkflowRepository workflowRepository;
    private final WorkerRepository workerRepository;
    private final OperationRepository operationRepository;

    public List<WorkflowDetail> list() {
        return workflowRepository.findAll().stream()
            .map(this::toDetailBasic)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<WorkflowDetail> getById(Long id) {
        return workflowRepository.findByIdWithDetails(id)
            .map(this::toDetail);
    }

    @Transactional
    public WorkflowDetail create(WorkflowRequest request) {
        Workflow workflow = Workflow.builder()
            .name(request.getName())
            .description(request.getDescription())
            .build();

        Map<String, WorkflowNode> tempIdToNode = new HashMap<>();

        if (request.getNodes() != null) {
            for (NodeRequest nr : request.getNodes()) {
                WorkflowNode node = WorkflowNode.builder()
                    .tempId(nr.getTempId())
                    .operation(operationRepository.findById(nr.getOperationId())
                        .orElseThrow(() -> new RuntimeException("NOT_FOUND:操作不存在")))
                    .workerId(nr.getWorkerId())
                    .x(nr.getX())
                    .y(nr.getY())
                    .build();
                workflow.addNode(node);
                tempIdToNode.put(nr.getTempId(), node);
            }
        }

        if (request.getEdges() != null) {
            for (EdgeRequest er : request.getEdges()) {
                WorkflowNode source = tempIdToNode.get(er.getSourceTempId());
                WorkflowNode target = tempIdToNode.get(er.getTargetTempId());
                if (source != null && target != null) {
                    WorkflowEdge edge = WorkflowEdge.builder()
                        .sourceNode(source)
                        .targetNode(target)
                        .build();
                    workflow.addEdge(edge);
                }
            }
        }

        Workflow saved = workflowRepository.save(workflow);
        return toDetail(saved);
    }

    @Transactional
    public WorkflowDetail update(Long id, WorkflowRequest request) {
        Workflow workflow = workflowRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("NOT_FOUND:设计不存在"));

        workflow.setName(request.getName());
        workflow.setDescription(request.getDescription());

        workflow.getNodes().clear();
        workflow.getEdges().clear();

        Map<String, WorkflowNode> tempIdToNode = new HashMap<>();

        if (request.getNodes() != null) {
            for (NodeRequest nr : request.getNodes()) {
                WorkflowNode node = WorkflowNode.builder()
                    .tempId(nr.getTempId())
                    .operation(operationRepository.findById(nr.getOperationId())
                        .orElseThrow(() -> new RuntimeException("NOT_FOUND:操作不存在")))
                    .workerId(nr.getWorkerId())
                    .x(nr.getX())
                    .y(nr.getY())
                    .build();
                workflow.addNode(node);
                tempIdToNode.put(nr.getTempId(), node);
            }
        }

        if (request.getEdges() != null) {
            for (EdgeRequest er : request.getEdges()) {
                WorkflowNode source = tempIdToNode.get(er.getSourceTempId());
                WorkflowNode target = tempIdToNode.get(er.getTargetTempId());
                if (source != null && target != null) {
                    WorkflowEdge edge = WorkflowEdge.builder()
                        .sourceNode(source)
                        .targetNode(target)
                        .build();
                    workflow.addEdge(edge);
                }
            }
        }

        Workflow saved = workflowRepository.save(workflow);
        return toDetail(saved);
    }

    @Transactional
    public void delete(Long id) {
        if (!workflowRepository.existsById(id)) {
            throw new RuntimeException("NOT_FOUND:设计不存在");
        }
        workflowRepository.deleteById(id);
    }

    private WorkflowDetail toDetailBasic(Workflow w) {
        return WorkflowDetail.builder()
            .id(w.getId())
            .name(w.getName())
            .description(w.getDescription())
            .version(w.getVersion())
            .createdAt(w.getCreatedAt())
            .updatedAt(w.getUpdatedAt())
            .build();
    }

    private WorkflowDetail toDetail(Workflow w) {
        List<NodeDetail> nodes = w.getNodes().stream()
            .map(n -> {
                String workerName = null;
                String workerNickname = null;
                String workerAvatar = null;
                if (n.getWorkerId() != null) {
                    var workerOpt = workerRepository.findById(n.getWorkerId());
                    if (workerOpt.isPresent()) {
                        var w = workerOpt.get();
                        workerName = w.getName();
                        workerNickname = w.getNickname();
                        workerAvatar = w.getAvatar();
                    }
                }
                return NodeDetail.builder()
                    .id(n.getId())
                    .tempId(n.getTempId())
                    .operationId(n.getOperation().getId())
                    .operationName(n.getOperation().getName())
                    .workerId(n.getWorkerId())
                    .workerName(workerName)
                    .workerNickname(workerNickname)
                    .workerAvatar(workerAvatar)
                    .x(n.getX())
                    .y(n.getY())
                    .build();
            })
            .collect(Collectors.toList());

        List<EdgeDetail> edges = w.getEdges().stream()
            .map(e -> EdgeDetail.builder()
                .id(e.getId())
                .sourceNodeId(e.getSourceNode().getId())
                .targetNodeId(e.getTargetNode().getId())
                .build())
            .collect(Collectors.toList());

        return WorkflowDetail.builder()
            .id(w.getId())
            .name(w.getName())
            .description(w.getDescription())
            .version(w.getVersion())
            .nodes(nodes)
            .edges(edges)
            .createdAt(w.getCreatedAt())
            .updatedAt(w.getUpdatedAt())
            .build();
    }
}
