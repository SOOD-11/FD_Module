package com.example.demo.service;

import java.math.BigDecimal;
import java.util.List;

import com.example.demo.dto.AddAccountHolderRequest;
import com.example.demo.dto.CreateFDAccountRequest;
import com.example.demo.dto.EarlyWithdrawlRequest;
import com.example.demo.dto.FDAccountView;
import com.example.demo.dto.FDTransactionView;
import com.example.demo.dto.PrematureWithdrawalInquiryResponse;

public  interface  FDAccountService {
	
	FDAccountView createAccount(CreateFDAccountRequest request);
    FDAccountView findAccountByNumber(String accountNumber);
    List<FDAccountView> findAccounts(String idType, String value);
    List<FDTransactionView> getTransactionsForAccount(String accountNumber);
    FDAccountView addRoleToAccount(String accountNumber, AddAccountHolderRequest request);
    BigDecimal calculateMaturityAmount(BigDecimal principal, BigDecimal rate, Integer termInMonths);
    PrematureWithdrawalInquiryResponse getPrematureWithdrawalInquiry(String accountNumber);
    
    FDAccountView performEarlyWithdrawal(String accountNumber, EarlyWithdrawlRequest request);
}
