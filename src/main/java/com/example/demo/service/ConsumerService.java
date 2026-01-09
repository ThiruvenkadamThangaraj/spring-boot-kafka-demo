package com.example.demo.service;

import com.example.demo.entity.ConsumedMessage;
import com.example.demo.repository.ConsumedMessageRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class ConsumerService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ConsumedMessageRepository consumedMessageRepository;
    private final String dlqTopic;
    private final int maxRetries;
    private final long retryBackoffMs;

    public ConsumerService(KafkaTemplate<String, String> kafkaTemplate,
                           ConsumedMessageRepository consumedMessageRepository,
                           @Value("${kafka.dlq-topic}") String dlqTopic,
                           @Value("${kafka.consumer.max-retries:3}") int maxRetries,
                           @Value("${kafka.consumer.retry-backoff-ms:1000}") long retryBackoffMs) {
        this.kafkaTemplate = kafkaTemplate;
        this.consumedMessageRepository = consumedMessageRepository;
        this.dlqTopic = dlqTopic;
        this.maxRetries = maxRetries;
        this.retryBackoffMs = retryBackoffMs;
    }

    @KafkaListener(topics = "${kafka.topic}", groupId = "${kafka.group}")
    @Transactional
    public void listen(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        String key = record.key();
        String value = record.value();
        long offset = record.offset();
        int partition = record.partition();
        String topic = record.topic();
        
        // Check for duplicates
        if (consumedMessageRepository.existsByMessageKeyAndTopicAndPartitionIdAndOffsetId(
                key, topic, partition, offset)) {
            System.out.printf("DUPLICATE SKIPPED: key=%s partition=%d offset=%d%n", key, partition, offset);
            acknowledgment.acknowledge();
            return;
        }
        
        System.out.printf("Consumed message: key=%s value=%s partition=%d offset=%d%n", key, value, partition, offset);

        ConsumedMessage consumedMessage = new ConsumedMessage();
        consumedMessage.setMessageKey(key);
        consumedMessage.setTopic(topic);
        consumedMessage.setPartitionId(partition);
        consumedMessage.setOffsetId(offset);
        consumedMessage.setMessageValue(value);
        consumedMessage.setConsumedAt(LocalDateTime.now());
        consumedMessage.setRetryCount(0);

        int attempt = 0;
        while (true) {
            try {
                attempt++;
                consumedMessage.setRetryCount(attempt);

                // Business processing
                processMessage(key, value);

                // Mark as success and save to DB
                consumedMessage.setProcessingStatus("SUCCESS");
                consumedMessageRepository.save(consumedMessage);
                
                // On successful processing, commit offset
                acknowledgment.acknowledge();
                System.out.println("Offset committed for offset=" + offset);
                break;
            } catch (Exception ex) {
                System.err.println("Processing failed for offset=" + offset + " attempt=" + attempt + " error=" + ex.getMessage());
                if (attempt >= maxRetries) {
                    // Mark as failed and save to DB
                    consumedMessage.setProcessingStatus("FAILED");
                    consumedMessage.setErrorMessage(ex.getMessage());
                    consumedMessageRepository.save(consumedMessage);
                    
                    // send to DLQ with metadata
                    sendToDlq(record, ex);
                    // acknowledge to avoid reprocessing
                    acknowledgment.acknowledge();
                    System.out.println("Sent to DLQ and committed offset=" + offset);
                    break;
                } else {
                    try {
                        Thread.sleep(retryBackoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        
                        // Mark as failed and save to DB
                        consumedMessage.setProcessingStatus("FAILED");
                        consumedMessage.setErrorMessage(ie.getMessage());
                        consumedMessageRepository.save(consumedMessage);
                        
                        // send to DLQ on interruption
                        sendToDlq(record, ie);
                        acknowledgment.acknowledge();
                        break;
                    }
                }
            }
        }
    }

    private void processMessage(String key, String value) {
        // TODO: replace with real business logic. Example placeholder below.
        if (value == null) {
            throw new IllegalArgumentException("Empty message");
        }
        // Example: parse JSON, call services, etc.
    }

    private void sendToDlq(ConsumerRecord<String, String> record, Exception ex) {
        String dlqPayload = String.format("{\"originalTopic\":\"%s\",\"partition\":%d,\"offset\":%d,\"key\":\"%s\",\"value\":%s,\"error\":\"%s\"}",
                record.topic(), record.partition(), record.offset(), record.key(), record.value(), ex.getMessage());
        kafkaTemplate.send(dlqTopic, record.key(), dlqPayload);
    }
}
