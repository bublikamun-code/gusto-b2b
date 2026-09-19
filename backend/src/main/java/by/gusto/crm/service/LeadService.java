package by.gusto.crm.service;

import by.gusto.audit.AuditService;
import by.gusto.auth.entity.Role;
import by.gusto.auth.entity.User;
import by.gusto.common.exception.ErrorCode;
import by.gusto.common.exception.GustoException;
import by.gusto.crm.dto.CrmDtos.AssignLeadRequest;
import by.gusto.crm.dto.CrmDtos.CreateLeadRequest;
import by.gusto.crm.dto.CrmDtos.LeadResponse;
import by.gusto.crm.dto.CrmDtos.LeadStatusRequest;
import by.gusto.crm.entity.LeadEntity;
import by.gusto.crm.entity.LeadEntity.Status;
import by.gusto.crm.repository.LeadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Лиды (S29): воронка NEW → IN_PROGRESS → QUALIFIED → WON/LOST (2.8),
 * назначение менеджеру; заявки с сайта конвертируются в лиды в S31.
 * Права: ADMIN — все; MANAGER — свои и пул «не назначено» (2.7).
 */
@Service
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
public class LeadService {

    private static final Map<Status, Set<Status>> TRANSITIONS = Map.of(
            Status.NEW, EnumSet.of(Status.IN_PROGRESS, Status.LOST),
            Status.IN_PROGRESS, EnumSet.of(Status.QUALIFIED, Status.LOST),
            Status.QUALIFIED, EnumSet.of(Status.WON, Status.LOST),
            Status.WON, EnumSet.noneOf(Status.class),
            Status.LOST, EnumSet.noneOf(Status.class));

    private final LeadRepository leadRepository;
    private final AuditService auditService;

    @Transactional
    public LeadResponse create(CreateLeadRequest request, User actor) {
        LeadEntity lead = leadRepository.save(LeadEntity.builder()
                .source(request.getSource())
                .name(request.getName())
                .phone(request.getPhone())
                .email(request.getEmail())
                .companyName(request.getCompanyName())
                .message(request.getMessage())
                .assignedManagerId(request.getManagerId())
                .build());

        auditService.append(actor.getId(), "LEAD_CREATE", "lead", lead.getId(),
                null,
                Map.of("name", lead.getName(),
                        "managerId", lead.getAssignedManagerId() == null
                                ? "" : lead.getAssignedManagerId().toString()));
        return toResponse(lead);
    }

    @Transactional(readOnly = true)
    public Page<LeadResponse> list(User actor, String scope, Status status, int page, int size) {
        PageRequest pageable = PageRequest.of(page, Math.min(size, 100));
        UUID actorId = actor.getId();
        boolean manager = actor.getRole() == Role.MANAGER;
        String effective = scope == null || scope.isBlank()
                ? (manager ? "pool" : "all") : scope;
        if (manager && "all".equals(effective)) {
            throw new GustoException(ErrorCode.VALIDATION_FAILED,
                    "Менеджеру доступны scope=mine|unassigned|pool");
        }

        Page<LeadEntity> leads = switch (effective) {
            case "mine" -> status == null
                    ? leadRepository.findAllByAssignedManagerIdOrderByCreatedAtDesc(actorId, pageable)
                    : leadRepository.findAllByAssignedManagerIdAndStatusOrderByCreatedAtDesc(actorId, status, pageable);
            case "unassigned" -> status == null
                    ? leadRepository.findAllByAssignedManagerIdIsNullOrderByCreatedAtDesc(pageable)
                    : leadRepository.findAllByAssignedManagerIdIsNullAndStatusOrderByCreatedAtDesc(status, pageable);
            case "pool" -> status == null
                    ? leadRepository.findAllByAssignedManagerIdOrAssignedManagerIdIsNullOrderByCreatedAtDesc(actorId, pageable)
                    : leadRepository.findAllByAssignedManagerIdOrAssignedManagerIdIsNullAndStatusOrderByCreatedAtDesc(actorId, status, pageable);
            case "all" -> status == null
                    ? leadRepository.findAllByOrderByCreatedAtDesc(pageable)
                    : leadRepository.findAllByStatusOrderByCreatedAtDesc(status, pageable);
            default -> throw new GustoException(ErrorCode.VALIDATION_FAILED,
                    "scope: mine|unassigned|pool|all");
        };
        return leads.map(this::toResponse);
    }

    @Transactional
    public LeadResponse assign(UUID leadId, AssignLeadRequest request, User actor) {
        LeadEntity lead = leadRepository.findById(leadId)
                .orElseThrow(() -> new GustoException(ErrorCode.NOT_FOUND, "Лид не найден"));
        UUID before = lead.getAssignedManagerId();
        lead.setAssignedManagerId(request.getManagerId());
        leadRepository.save(lead);

        auditService.append(actor.getId(), "LEAD_ASSIGN", "lead", leadId,
                Map.of("managerId", before == null ? "" : before.toString()),
                Map.of("managerId", request.getManagerId().toString()));
        return toResponse(lead);
    }

    @Transactional
    public LeadResponse changeStatus(UUID leadId, LeadStatusRequest request, User actor) {
        LeadEntity lead = leadRepository.findById(leadId)
                .orElseThrow(() -> new GustoException(ErrorCode.NOT_FOUND, "Лид не найден"));
        requireCanManage(lead, actor);

        Status current = lead.getStatus();
        Status target = request.getStatus();
        if (!TRANSITIONS.get(current).contains(target)) {
            throw new GustoException(ErrorCode.LEAD_STATUS_TRANSITION,
                    "Переход " + current + " → " + target + " недопустим");
        }
        lead.setStatus(target);
        leadRepository.save(lead);

        auditService.append(actor.getId(), "LEAD_STATUS", "lead", leadId,
                Map.of("status", current.name()), Map.of("status", target.name()));
        return toResponse(lead);
    }

    /** Права на воронку: ADMIN — все; менеджер — свои и лиды из пула (взятие закрепляет). */
    private void requireCanManage(LeadEntity lead, User actor) {
        if (actor.getRole() == Role.ADMIN) {
            return;
        }
        boolean assigned = actor.getId().equals(lead.getAssignedManagerId());
        boolean pool = lead.getAssignedManagerId() == null;
        if (!assigned && !pool) {
            throw new GustoException(ErrorCode.ACCESS_DENIED);
        }
        if (pool) {
            lead.setAssignedManagerId(actor.getId());
            leadRepository.save(lead);
        }
    }

    private LeadResponse toResponse(LeadEntity lead) {
        return new LeadResponse(lead.getId(), lead.getSource(), lead.getName(), lead.getPhone(),
                lead.getEmail(), lead.getCompanyName(), lead.getMessage(),
                lead.getStatus().name(), lead.getAssignedManagerId(), lead.getCreatedAt());
    }
}
