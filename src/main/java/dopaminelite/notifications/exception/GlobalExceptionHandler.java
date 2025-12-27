package dopaminelite.notifications.exception;

import dopaminelite.notifications.dto.common.ErrorObject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorObject> handleResourceNotFoundException(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        
        ErrorObject error = ErrorObject.builder()
            .code(String.valueOf(HttpStatus.NOT_FOUND.value()))
            .message(ex.getMessage())
            .build();
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }
    
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorObject> handleRuntimeException(RuntimeException ex) {
        log.error("Runtime exception occurred", ex);
        
        // Check if it's a not found exception (case-insensitive)
        if (ex.getMessage() != null && Pattern.compile("not found", Pattern.CASE_INSENSITIVE).matcher(ex.getMessage()).find()) {
            ErrorObject error = ErrorObject.builder()
                .code(String.valueOf(HttpStatus.NOT_FOUND.value()))
                .message(ex.getMessage())
                .build();
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }
        
        ErrorObject error = ErrorObject.builder()
            .code(String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.value()))
            .message(ex.getMessage())
            .build();
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorObject> handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, Object> details = new HashMap<>();
        
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            details.put(fieldName, errorMessage);
        });
        
        ErrorObject error = ErrorObject.builder()
            .code(String.valueOf(HttpStatus.BAD_REQUEST.value()))
            .message("Validation failed")
            .details(details)
            .build();
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
    
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorObject> handleConstraintViolationException(ConstraintViolationException ex) {
        Map<String, Object> details = new HashMap<>();
        
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            String propertyPath = violation.getPropertyPath().toString();
            String message = violation.getMessage();
            details.put(propertyPath, message);
        }
        
        ErrorObject error = ErrorObject.builder()
            .code(String.valueOf(HttpStatus.BAD_REQUEST.value()))
            .message("Validation failed")
            .details(details)
            .build();
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
    
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorObject> handleTypeMismatchException(MethodArgumentTypeMismatchException ex) {
        Map<String, Object> details = new HashMap<>();
        details.put("parameter", ex.getName());
        details.put("invalidValue", ex.getValue());
        details.put("requiredType", ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown");
        
        ErrorObject error = ErrorObject.builder()
            .code(String.valueOf(HttpStatus.BAD_REQUEST.value()))
            .message("Invalid parameter type")
            .details(details)
            .build();
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
    
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorObject> handleDataIntegrityViolationException(DataIntegrityViolationException ex) {
        log.error("Data integrity violation occurred", ex);
        
        ErrorObject error = ErrorObject.builder()
            .code(String.valueOf(HttpStatus.BAD_REQUEST.value()))
            .message("Invalid data provided. Please check your request and try again.")
            .build();
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
    
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorObject> handleDataAccessException(DataAccessException ex) {
        log.error("Database access error occurred", ex);
        
        ErrorObject error = ErrorObject.builder()
            .code(String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.value()))
            .message("A database error occurred. Please try again later.")
            .build();
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
    
    @ExceptionHandler(SQLException.class)
    public ResponseEntity<ErrorObject> handleSQLException(SQLException ex) {
        log.error("SQL exception occurred", ex);
        
        ErrorObject error = ErrorObject.builder()
            .code(String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.value()))
            .message("A database error occurred. Please try again later.")
            .build();
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorObject> handleGeneralException(Exception ex) {
        log.error("Unexpected exception occurred", ex);
        
        ErrorObject error = ErrorObject.builder()
            .code(String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.value()))
            .message("An unexpected error occurred")
            .build();
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
