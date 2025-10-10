package com.example.demo.util.impl;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

import com.example.demo.repository.FdAccountRepository;
import com.example.demo.util.AccountNumberGenerator;
@Component

public class DefaultAccountNumberGenerator implements AccountNumberGenerator {

    private static final String BRANCH_CODE = "101";
    private final FdAccountRepository fdAccountRepository;
    private final SecureRandom random = new SecureRandom();

    public DefaultAccountNumberGenerator(FdAccountRepository fdAccountRepository) {
        this.fdAccountRepository = fdAccountRepository;
    }

    @Override
    public String generate() {
        String accountNumber;
        do {
            // 1. Generate 6-digit random part
            int randomNumber = this.random.nextInt(900000) + 100000;
            String sequentialPart = String.valueOf(randomNumber);

            // 2. Construct 9-digit base
            String baseNumber = BRANCH_CODE + sequentialPart;

            // 3. Calculate Luhn check digit
            int checkDigit = calculateLuhnCheckDigit(baseNumber);

            // 4. Form the final number
            accountNumber = baseNumber + checkDigit;
        } while (fdAccountRepository.findByAccountNumber(accountNumber).isPresent()); // 5. Ensure uniqueness

        return accountNumber;
    }

    private int calculateLuhnCheckDigit(String number) {
        int sum = 0;
        boolean alternate = false;
        for (int i = number.length() - 1; i >= 0; i--) {
            int n = Integer.parseInt(number.substring(i, i + 1));
            if (alternate) {
                n *= 2;
                if (n > 9) {
                    n = (n % 10) + 1;
                }
            }
            sum += n;
            alternate = !alternate;
        }
        return (sum * 9) % 10;
    }
}