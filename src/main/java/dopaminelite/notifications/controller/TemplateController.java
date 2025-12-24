package dopaminelite.notifications.controller;

import dopaminelite.notifications.dto.*;
import dopaminelite.notifications.entity.enums.TemplateType;
import dopaminelite.notifications.service.TemplateService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for notification template management.
 * 
 * Endpoints:
 * - GET /api/v1/templates - List templates with filters
 * - POST /api/v1/templates - Create new template
 * - GET /api/v1/templates/{id} - Get template details
 * - PUT /api/v1/templates/{id} - Update template
 * - DELETE /api/v1/templates/{id} - Delete template
 */
@RestController
@RequestMapping("/api/v1/templates")
@RequiredArgsConstructor
@Validated
public class TemplateController {
    
    private final TemplateService templateService;
    
    /**
     * List notification templates.
     * 
     * OpenAPI: GET /templates
     * Supports filtering by type and searching by name/ID.
     */
    @GetMapping
    public ResponseEntity<TemplateListResponse> listTemplates(
        @RequestParam(required = false) TemplateType type,
        @RequestParam(required = false) String search,
        @RequestParam(required = false, defaultValue = "20") @Min(1) @Max(100) Integer limit,
        @RequestParam(required = false, defaultValue = "0") @Min(0) Integer offset
    ) {
        TemplateListResponse response = templateService.listTemplates(type, search, limit, offset);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get template details.
     * 
     * OpenAPI: GET /templates/{templateId}
     */
    @GetMapping("/{templateId}")
    public ResponseEntity<TemplateDto> getTemplate(@PathVariable UUID templateId) {
        TemplateDto template = templateService.getTemplate(templateId);
        return ResponseEntity.ok(template);
    }
    
    /**
     * Create notification template.
     * 
     * OpenAPI: POST /templates
     * 
     * Note: createdBy should ideally come from authenticated user context.
     * For now, we'll use a placeholder from request header or default.
     */
    @PostMapping
    public ResponseEntity<TemplateDto> createTemplate(
        @Valid @RequestBody CreateTemplateRequest request,
        @RequestHeader(value = "X-User-Id", required = false) UUID createdBy
    ) {
        // Default to a system user if not provided
        UUID creator = createdBy != null ? createdBy : UUID.fromString("00000000-0000-0000-0000-000000000000");
        
        TemplateDto template = templateService.createTemplate(request, creator);
        return ResponseEntity.status(HttpStatus.CREATED).body(template);
    }
    
    /**
     * Update notification template.
     * 
     * OpenAPI: PUT /templates/{templateId}
     */
    @PutMapping("/{templateId}")
    public ResponseEntity<TemplateDto> updateTemplate(
        @PathVariable UUID templateId,
        @Valid @RequestBody UpdateTemplateRequest request
    ) {
        TemplateDto template = templateService.updateTemplate(templateId, request);
        return ResponseEntity.ok(template);
    }
    
    /**
     * Delete notification template.
     * 
     * OpenAPI: DELETE /templates/{templateId}
     * 
     * Returns 409 Conflict if template has been used.
     */
    @DeleteMapping("/{templateId}")
    public ResponseEntity<Void> deleteTemplate(@PathVariable UUID templateId) {
        templateService.deleteTemplate(templateId);
        return ResponseEntity.noContent().build();
    }
}
