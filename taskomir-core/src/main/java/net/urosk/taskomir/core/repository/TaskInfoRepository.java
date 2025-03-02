package net.urosk.taskomir.core.repository;


import net.urosk.taskomir.core.lib.TaskInfo;
import net.urosk.taskomir.core.lib.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;

public interface TaskInfoRepository extends MongoRepository<TaskInfo, String> {

    Page<TaskInfo> findByStatusInOrderByCreatedAtDesc(Collection<TaskStatus> statuses, Pageable pageable);
    Page<TaskInfo> findByStatusOrderByCreatedAtDesc(TaskStatus status, Pageable pageable);
    void deleteByStatus(TaskStatus taskStatus);
    List<TaskInfo> findByStatusOrderByCreatedAtDesc(TaskStatus taskStatus);
    List<TaskInfo> findByParentIdAndStatusIn(String id, List<TaskStatus> list);
}