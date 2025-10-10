package com.example.demo.service;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.example.demo.events.AccountClosedEvent;
import com.example.demo.events.AccountCreatedEvent;
import com.example.demo.events.AccountMaturedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaProducerService {
	
	   private static final String TOPIC = "fd.account.created";
	   private static final String MATURED_TOPIC = "fd.account.matured";
	   private static final String CLOSED_TOPIC = "fd.account.closed"; // Topic for closed accounts
	   private final KafkaTemplate<String, Object> kafkaTemplate;
	    

	    public void sendAccountCreatedEvent(AccountCreatedEvent event) {
	     
			 log.info("Publishing account created event to topic {}: {}", TOPIC, event);
	        try {
	            this.kafkaTemplate.send(TOPIC, event.accountNumber(), event);
	        } catch (Exception e) {
	    log.error("Failed to send message to Kafka", e);
	            // In a real application, you might want to save the failed event to a database table to retry later
	        }
	        
	        
	    }
	        
	     // --- ADD THIS NEW METHOD ---
	        public void  sendAccountMaturedEvent(AccountMaturedEvent event) {
	            log.info("Publishing account matured event to topic {}: {}", MATURED_TOPIC, event);
	            try {
	                this.kafkaTemplate.send(MATURED_TOPIC, event.accountNumber(), event);
	            } catch (Exception e) {
	                log.error("Failed to send account matured message to Kafka", e);
	            }
	            
	            
	           
	
	

}
	        
	        
	        
	        
	        public void sendAccountClosedEvent(AccountClosedEvent event) {
                log.info("Publishing account closed event to topic {}: {}", CLOSED_TOPIC, event);
                try {
                    // We use the account number as the key to ensure messages for the same account go to the same partition.
                    this.kafkaTemplate.send(CLOSED_TOPIC, event.accountNumber(), event);
                } catch (Exception e) {
                    // In a real application, you might save the failed event to a database to retry later.
                    log.error("Failed to send account closed message to Kafka", e);
                }
            }
}
