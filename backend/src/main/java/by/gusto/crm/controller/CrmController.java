package by.gusto.crm.controller;

import by.gusto.auth.entity.User;
import by.gusto.auth.service.AuthContext;
import by.gusto.common.api.ApiResponse;
import by.gusto.crm.dto.CrmDtos.AssignLeadRequest;
import by.gusto.crm.dto.CrmDtos.CreateLeadRequest;
import by.gusto.crm.dto.CrmDtos.CreateNoteRequest;
import by.gusto.crm.dto.CrmDtos.CreateTaskRequest;
import by.gusto.crm.dto.CrmDtos.LeadResponse;
import by.gusto.crm.dto.CrmDtos.LeadStatusRequest;
import by.gusto.crm.dto.CrmDtos.NoteResponse;
import by.gusto.crm.dto.CrmDtos.TaskResponse;
import by.gusto.crm.entity.LeadEntity;
import by.gusto.crm.service.CrmDashboardService;
import by.gusto.crm.service.CrmWorkService;
import by.gusto.crm.service.LeadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CRM (S29): лиды/воронка, задачи, заметки, дашборд руководителя.
 * Права — MANAGER/ADMIN (матрица 2.1); бухгалтеру CRM недоступна.
 */
@RestController
@RequestMapping("/api/v1/crm")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
public class CrmController {

    private final LeadService leadService;
    private final CrmWorkService crmWorkService;
    private final CrmDashboardService dashboardService;
    private final AuthContext authContext;

    // ----- лиды ------------------------------------------------------------------

    @PostMapping("/leads")
    public ResponseEntity<ApiResponse<LeadResponse>> createLead(@Valid @RequestBody CreateLeadRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                leadService.create(request, authContext.getCurrentUser())));
    }

    @GetMapping("/leads")
    public ResponseEntity<ApiResponse<List<LeadResponse>>> listLeads(
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) LeadEntity.Status status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<LeadResponse> result = leadService.list(authContext.getCurrentUser(), scope, status, page, size);
        return ResponseEntity.ok(ApiResponse.success(result.getContent()));
    }

    @PostMapping("/leads/{id}/assign")
    public ResponseEntity<ApiResponse<LeadResponse>> assignLead(
            @PathVariable UUID id,
            @Valid @RequestBody AssignLeadRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                leadService.assign(id, request, authContext.getCurrentUser())));
    }

    @PostMapping("/leads/{id}/status")
    public ResponseEntity<ApiResponse<LeadResponse>> changeLeadStatus(
            @PathVariable UUID id,
            @Valid @RequestBody LeadStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                leadService.changeStatus(id, request, authContext.getCurrentUser())));
    }

    // ----- задачи ----------------------------------------------------------------

    @PostMapping("/tasks")
    public ResponseEntity<ApiResponse<TaskResponse>> createTask(@Valid @RequestBody CreateTaskRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                crmWorkService.createTask(request, authContext.getCurrentUser())));
    }

    @GetMapping("/tasks")
    public ResponseEntity<ApiResponse<List<TaskResponse>>> listTasks(
            @RequestParam(required = false) String scope,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<TaskResponse> result = crmWorkService.listTasks(authContext.getCurrentUser(), scope, page, size);
        return ResponseEntity.ok(ApiResponse.success(result.getContent()));
    }

    @PostMapping("/tasks/{id}/status")
    public ResponseEntity<ApiResponse<TaskResponse>> changeTaskStatus(
            @PathVariable UUID id,
            @RequestParam String status) {
        return ResponseEntity.ok(ApiResponse.success(
                crmWorkService.changeTaskStatus(id, status, authContext.getCurrentUser())));
    }

    // ----- заметки ---------------------------------------------------------------

    @PostMapping("/notes")
    public ResponseEntity<ApiResponse<NoteResponse>> addNote(@Valid @RequestBody CreateNoteRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                crmWorkService.addNote(request, authContext.getCurrentUser())));
    }

    @GetMapping("/notes")
    public ResponseEntity<ApiResponse<List<NoteResponse>>> notes(@RequestParam UUID companyId) {
        return ResponseEntity.ok(ApiResponse.success(crmWorkService.notes(companyId)));
    }

    // ----- дашборд ---------------------------------------------------------------

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<Map<String, Object>>> dashboard(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        User actor = authContext.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.success(dashboardService.dashboard(actor, from, to)));
    }
}
