package com.example.demo.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class ProductCommunicationResponse {
    
    private List<Communication> content;
    private Pageable pageable;
    private int totalElements;
    private int totalPages;
    private boolean last;
    private Sort sort;
    private boolean first;
    private int numberOfElements;
    private int size;
    private int number;
    private boolean empty;
    
    @Data
    public static class Communication {
        private String commId;
        private String commCode;
        private String communicationType;
        private String channel;
        private String event;
        private String template;
        private int frequencyLimit;
    }
    
    @Data
    public static class Pageable {
        private int pageNumber;
        private int pageSize;
        private Sort sort;
        private int offset;
        private boolean unpaged;
        private boolean paged;
    }
    
    @Data
    public static class Sort {
        private boolean sorted;
        private boolean unsorted;
        private boolean empty;
    }
}
