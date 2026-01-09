package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "consumed_messages", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"message_key", "topic", "partition_id", "offset_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConsumedMessage {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "message_key", nullable = false)
    private String messageKey;
    
    @Column(name = "topic", nullable = false)
    private String topic;
    
    @Column(name = "partition_id", nullable = false)
    private Integer partitionId;
    
    @Column(name = "offset_id", nullable = false)
    private Long offsetId;
    
    @Column(name = "message_value", columnDefinition = "TEXT")
    private String messageValue;
    
    @Column(name = "consumed_at", nullable = false)
    private LocalDateTime consumedAt;
    
    @Column(name = "processing_status")
    private String processingStatus; // SUCCESS, FAILED, RETRY
    
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    
    @Column(name = "retry_count")
    private Integer retryCount = 0;
}
