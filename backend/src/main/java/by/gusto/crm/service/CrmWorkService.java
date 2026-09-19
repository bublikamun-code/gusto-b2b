package by.gusto.crm.service;

import by.gusto.auth.entity.Role;
import by.gusto.auth.entity.User;
import by.gusto.common.exception.ErrorCode;
import by.gusto.common.exception.GustoException;
import by.gusto.crm.dto.CrmDtos.CreateNoteRequest;
import by.gusto.crm.dto.CrmDtos.CreateTaskRequest;
import by.gusto.crm.dto.CrmDtos.NoteResponse;
import by.gusto.crm.dto.CrmDtos.TaskResponse;
import by.gusto.crm.entity.CrmNoteEntity;
import by.gusto.crm.entity.CrmTaskEntity;
import by.gusto.crm.repository.CrmNoteRepository;
import by.gusto.crm.repository.CrmTaskRepository;
import by.gusto.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Задачи и заметки (S29): задачи с сроком и статусами OPEN/DONE/CANCELLED,
 * заметки — история взаимодействия в карточке компании.
 * Права: ADMIN/MANAGER (матрица 2.1); менеджер видит свои задачи.
 */
@Service
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
public class CrmWorkService {

    private final CrmTaskRepository taskRepository;
    private final CrmNoteRepository noteRepository;
    private final UserRepository userRepository;
    private final by.gusto.company.repository.CompanyRepository companyRepository;

    // ----- задачи ----------------------------------------------------------------

    @Transactional
    public TaskResponse createTask(CreateTaskRequest request, User actor) {
        UUID assigneeId = request.getAssigneeId() != null ? request.getAssigneeId() : actor.getId();
        CrmTaskEntity task = taskRepository.save(CrmTaskEntity.builder()
                .assigneeId(assigneeId)
                .companyId(request.getCompanyId())
                .title(request.getTitle())
                .description(request.getDescription())
                .dueDate(request.getDueDate())
                .build());
        return toResponse(task);
    }

    @Transactional(readOnly = true)
    public Page<TaskResponse> listTasks(User actor, String scope, int page, int size) {
        PageRequest pageable = PageRequest.of(page, Math.min(size, 100));
        boolean manager = actor.getRole() == Role.MANAGER;
        String effective = scope == null || scope.isBlank() ? "mine" : scope;
        if (manager && "all".equals(effective)) {
            throw new GustoException(ErrorCode.VALIDATION_FAILED,
                    "Менеджеру доступны scope=mine|overdue");
        }
        Page<CrmTaskEntity> tasks = switch (effective) {
            case "mine" -> taskRepository.findAllByAssigneeIdOrderByCreatedAtDesc(actor.getId(), pageable);
            case "overdue" -> taskRepository.findAllByAssigneeIdAndStatusOrderByDueDateAsc(
                    actor.getId(), CrmTaskEntity.Status.OPEN, pageable);
            case "all" -> taskRepository.findAllByOrderByCreatedAtDesc(pageable);
            default -> throw new GustoException(ErrorCode.VALIDATION_FAILED,
                    "scope: mine|overdue|all");
        };
        return tasks.map(this::toResponse);
    }

    @Transactional
    public TaskResponse changeTaskStatus(UUID taskId, String target, User actor) {
        CrmTaskEntity task = taskRepository.findById(taskId)
                .orElseThrow(() -> new GustoException(ErrorCode.NOT_FOUND, "Задача не найдена"));
        if (actor.getRole() == Role.MANAGER && !actor.getId().equals(task.getAssigneeId())) {
            throw new GustoException(ErrorCode.ACCESS_DENIED);
        }
        CrmTaskEntity.Status status;
        try {
            status = CrmTaskEntity.Status.valueOf(target);
        } catch (IllegalArgumentException e) {
            throw new GustoException(ErrorCode.VALIDATION_FAILED, "Неизвестный статус задачи");
        }
        if (task.getStatus() != CrmTaskEntity.Status.OPEN) {
            throw new GustoException(ErrorCode.CONFLICT,
                    "Закрытая задача не меняет статус");
        }
        task.setStatus(status);
        taskRepository.save(task);
        return toResponse(task);
    }

    // ----- заметки ---------------------------------------------------------------

    @Transactional
    public NoteResponse addNote(CreateNoteRequest request, User actor) {
        if (!companyRepository.existsById(request.getCompanyId())) {
            throw new GustoException(ErrorCode.NOT_FOUND, "Компания не найдена");
        }
        CrmNoteEntity note = noteRepository.save(CrmNoteEntity.builder()
                .companyId(request.getCompanyId())
                .authorId(actor.getId())
                .body(request.getBody())
                .build());
        return toResponse(note);
    }

    @Transactional(readOnly = true)
    public List<NoteResponse> notes(UUID companyId) {
        return noteRepository.findAllByCompanyIdOrderByCreatedAtDesc(companyId).stream()
                .map(this::toResponse)
                .toList();
    }

    // ----- internals -------------------------------------------------------------

    private TaskResponse toResponse(CrmTaskEntity task) {
        boolean overdue = task.getStatus() == CrmTaskEntity.Status.OPEN
                && task.getDueDate() != null
                && task.getDueDate().isBefore(Instant.now());
        return new TaskResponse(task.getId(), task.getAssigneeId(), task.getCompanyId(),
                task.getTitle(), task.getDescription(), task.getDueDate(),
                task.getStatus().name(), overdue, task.getCreatedAt());
    }

    private NoteResponse toResponse(CrmNoteEntity note) {
        String authorName = userRepository.findById(note.getAuthorId())
                .map(User::getFullName).orElse(null);
        return new NoteResponse(note.getId(), note.getCompanyId(), note.getAuthorId(),
                authorName, note.getBody(), note.getCreatedAt());
    }
}
