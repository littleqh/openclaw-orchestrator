package com.openclaw.orchestrator.repository;

import com.openclaw.orchestrator.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    @Query("SELECT t FROM Task t LEFT JOIN FETCH t.stage WHERE t.instance.id = :instanceId")
    List<Task> findByInstanceId(Long instanceId);

    List<Task> findByWorkerId(Long workerId);

    @Query("SELECT t FROM Task t WHERE t.workerId = :workerId AND t.status = 'PENDING' ORDER BY t.priority DESC, t.createdAt ASC")
    List<Task> findPendingTasksForWorker(Long workerId, Pageable pageable);

    default Optional<Task> findPendingTaskForWorker(Long workerId) {
        List<Task> tasks = findPendingTasksForWorker(workerId, Pageable.ofSize(1));
        return tasks.isEmpty() ? Optional.empty() : Optional.of(tasks.get(0));
    }

    List<Task> findByStatus(Task.TaskStatus status);

    @Query("SELECT t FROM Task t WHERE t.instance.id = :instanceId AND t.status = 'PENDING'")
    Optional<Task> findPendingTaskByInstance(Long instanceId);

    @Query("SELECT t FROM Task t WHERE t.approverId = :approverId AND t.status = 'PENDING' AND t.stage.taskType = 'APPROVAL'")
    List<Task> findPendingApprovals(Long approverId);
}
