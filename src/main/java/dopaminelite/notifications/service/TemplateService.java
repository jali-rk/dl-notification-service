package dopaminelite.notifications.service;

import dopaminelite.notifications.dto.*;
import dopaminelite.notifications.entity.NotificationTemplate;
import dopaminelite.notifications.entity.enums.TemplateType;
import dopaminelite.notifications.exception.ResourceNotFoundException;
import dopaminelite.notifications.exception.ValidationException;
import dopaminelite.notifications.repository.NotificationTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service for managing notification templates.
 * Handles CRUD operations for reusable notification templates.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateService {
    
    private final NotificationTemplateRepository templateRepository;
    
    /**
     * List templates with optional filters and pagination.
     */
    @Transactional(readOnly = true)
    public TemplateListResponse listTemplates(TemplateType type, String search, int limit, int offset) {
        Pageable pageable = PageRequest.of(offset / limit, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        
        Page<NotificationTemplate> page;
        if (type != null && search != null && !search.isBlank()) {
            page = templateRepository.searchByType(type, search, pageable);
        } else if (type != null) {
            page = templateRepository.findByType(type, pageable);
        } else if (search != null && !search.isBlank()) {
            page = templateRepository.search(search, pageable);
        } else {
            page = templateRepository.findAll(pageable);
        }
        
        return TemplateListResponse.builder()
            .items(page.getContent().stream().map(this::toDto).toList())
            .total(page.getTotalElements())
            .build();
    }
    
    /**
     * Get a template by ID.
     */
    @Transactional(readOnly = true)
    public TemplateDto getTemplate(UUID templateId) {
        NotificationTemplate template = templateRepository.findById(templateId)
            .orElseThrow(() -> new ResourceNotFoundException("Template not found: " + templateId));
        return toDto(template);
    }
    
    /**
     * Create a new template.
     */
    @Transactional
    public TemplateDto createTemplate(CreateTemplateRequest request, UUID createdBy) {
        // Check if templateId already exists
        templateRepository.findByTemplateId(request.getTemplateId()).ifPresent(existing -> {
            throw new ValidationException("Template with ID '" + request.getTemplateId() + "' already exists");
        });
        
        NotificationTemplate template = new NotificationTemplate();
        template.setTemplateId(request.getTemplateId());
        template.setTemplateName(request.getTemplateName());
        template.setType(request.getType());
        template.setContentSinhala(request.getContentSinhala());
        template.setContentEnglish(request.getContentEnglish());
        template.setChannels(request.getChannels());
        template.setMetadata(request.getMetadata());
        template.setSentTimes(0);
        template.setCreatedBy(createdBy);
        
        template = templateRepository.save(template);
        log.info("Created template: {} ({})", template.getTemplateName(), template.getId());
        
        return toDto(template);
    }
    
    /**
     * Update an existing template.
     */
    @Transactional
    public TemplateDto updateTemplate(UUID templateId, UpdateTemplateRequest request) {
        NotificationTemplate template = templateRepository.findById(templateId)
            .orElseThrow(() -> new ResourceNotFoundException("Template not found: " + templateId));
        
        if (request.getTemplateName() != null) {
            template.setTemplateName(request.getTemplateName());
        }
        if (request.getType() != null) {
            template.setType(request.getType());
        }
        if (request.getContentSinhala() != null) {
            template.setContentSinhala(request.getContentSinhala());
        }
        if (request.getContentEnglish() != null) {
            template.setContentEnglish(request.getContentEnglish());
        }
        if (request.getChannels() != null) {
            template.setChannels(request.getChannels());
        }
        if (request.getMetadata() != null) {
            template.setMetadata(request.getMetadata());
        }
        
        template = templateRepository.save(template);
        log.info("Updated template: {} ({})", template.getTemplateName(), template.getId());
        
        return toDto(template);
    }
    
    /**
     * Delete a template.
     */
    @Transactional
    public void deleteTemplate(UUID templateId) {
        NotificationTemplate template = templateRepository.findById(templateId)
            .orElseThrow(() -> new ResourceNotFoundException("Template not found: " + templateId));
        
        // Check if template is in use
        if (template.getSentTimes() > 0) {
            throw new ValidationException("Cannot delete template that has been used. Template has been sent " + 
                template.getSentTimes() + " times.");
        }
        
        templateRepository.delete(template);
        log.info("Deleted template: {} ({})", template.getTemplateName(), template.getId());
    }
    
    /**
     * Increment sent times counter for a template.
     */
    @Transactional
    public void incrementSentTimes(UUID templateId) {
        NotificationTemplate template = templateRepository.findById(templateId).orElse(null);
        if (template != null) {
            template.setSentTimes(template.getSentTimes() + 1);
            templateRepository.save(template);
        }
    }
    
    /**
     * Map entity to DTO.
     */
    private TemplateDto toDto(NotificationTemplate template) {
        return TemplateDto.builder()
            .id(template.getId())
            .templateId(template.getTemplateId())
            .templateName(template.getTemplateName())
            .type(template.getType())
            .contentSinhala(template.getContentSinhala())
            .contentEnglish(template.getContentEnglish())
            .channels(template.getChannels())
            .metadata(template.getMetadata())
            .sentTimes(template.getSentTimes())
            .createdBy(template.getCreatedBy())
            .createdAt(template.getCreatedAt())
            .updatedAt(template.getUpdatedAt())
            .build();
    }
}
