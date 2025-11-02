package com.example.demo.service;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.example.demo.dto.StatementNotificationRequest;
import com.example.demo.events.AccountAlertEvent;
import com.example.demo.events.AccountClosedEvent;
import com.example.demo.events.AccountCreatedEvent;
import com.example.demo.events.AccountMaturedEvent;
import com.example.demo.events.CommunicationEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaProducerService {
	
	   private static final String TOPIC = "fd.account.created";
	   private static final String MATURED_TOPIC = "fd.account.matured";
	   private static final String CLOSED_TOPIC = "fd.account.closed"; // Topic for closed accounts
	   private static final String COMMUNICATION_TOPIC = "fd.communication"; // Topic for communications
	   private static final String STATEMENT_TOPIC = "statement"; // Topic for statements
	   private static final String ALERT_TOPIC = "alert"; // Topic for account alerts
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
	        
	        /**
	         * Send communication event to Kafka
	         * Used for various customer communications based on product configuration
	         */
	        public void sendCommunicationEvent(CommunicationEvent event) {
	            log.info("Publishing communication event to topic {}: type={}, channel={}", 
	                     COMMUNICATION_TOPIC, event.getCommunicationType(), event.getChannel());
	            try {
	                this.kafkaTemplate.send(COMMUNICATION_TOPIC, event.getAccountNumber(), event);
	            } catch (Exception e) {
	                log.error("Failed to send communication event to Kafka", e);
	            }
	        }
	        
	        /**
	         * Send statement notification to Kafka
	         * Used for generating and sending FD account statements
	         */
	        public void sendStatementNotification(StatementNotificationRequest request) {
	            log.info("Publishing statement notification to topic {}: account={}, type={}", 
	                     STATEMENT_TOPIC, 
	                     request.accountDetails().accountNumber(), 
	                     request.statementType());
	            try {
	                this.kafkaTemplate.send(STATEMENT_TOPIC, request.accountDetails().accountNumber(), request);
	            } catch (Exception e) {
	                log.error("Failed to send statement notification to Kafka", e);
	            }
	        }
	        
	        /**
	         * Send alert event to Kafka
	         * Used for notifying when accounts are created or modified
	         */
	        public void sendAlertEvent(AccountAlertEvent event) {
	            log.info("Publishing alert event to topic {}: type={}, account={}", 
	                     ALERT_TOPIC, event.alertType(), event.accountNumber());
	            try {
	                this.kafkaTemplate.send(ALERT_TOPIC, event.accountNumber(), event);
	            } catch (Exception e) {
	                log.error("Failed to send alert event to Kafka", e);
	            }
	        }
}
