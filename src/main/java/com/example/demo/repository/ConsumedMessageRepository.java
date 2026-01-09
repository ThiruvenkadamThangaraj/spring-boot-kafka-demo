package com.example.demo.repository;

import com.example.demo.entity.ConsumedMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConsumedMessageRepository extends JpaRepository<ConsumedMessage, Long> {
    
    boolean existsByMessageKeyAndTopicAndPartitionIdAndOffsetId(
        String messageKey, 
        String topic, 
        Integer partitionId, 
        Long offsetId
    );
    
    Optional<ConsumedMessage> findByMessageKeyAndTopicAndPartitionIdAndOffsetId(
        String messageKey, 
        String topic, 
        Integer partitionId, 
        Long offsetId
    );
}
