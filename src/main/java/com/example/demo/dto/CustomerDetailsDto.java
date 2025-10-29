package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CustomerDetailsDto(
    String customerId,
    String customerNumber,
    String firstName,
    String lastName,
    String dateOfBirth,
    String phoneNumber,
    String email,
    AddressDto address,
    String maskedPan
) {
    public record AddressDto(
        String line1,
        String line2,
        String city,
        String state,
        String country,
        String postalCode
    ) {}
}
