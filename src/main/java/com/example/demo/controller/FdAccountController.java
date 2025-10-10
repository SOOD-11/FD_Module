package com.example.demo.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.dto.AddAccountHolderRequest;
import com.example.demo.dto.CreateFDAccountRequest;
import com.example.demo.dto.EarlyWithdrawlRequest;
import com.example.demo.dto.FDAccountView;
import com.example.demo.dto.FDTransactionView;
import com.example.demo.dto.PrematureWithdrawalInquiryResponse;
import com.example.demo.service.FDAccountService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;


@RestController
@RequestMapping("/api/v1/accounts")


public class FdAccountController {
	
	
	

	
	
	 private  FDAccountService fdAccountService;

	public FdAccountController(FDAccountService fdAccountService) {
	        this.fdAccountService = fdAccountService;
	    }

    @PostMapping
    public ResponseEntity<FDAccountView> createFdAccount(@Valid @RequestBody CreateFDAccountRequest request) {
        FDAccountView createdAccount = fdAccountService.createAccount(request);
        return new ResponseEntity<>(createdAccount, HttpStatus.CREATED);
    }

    @GetMapping("/search")
    public ResponseEntity<List<FDAccountView>> findAccounts(@RequestParam("idType") String idType,
            @RequestParam("value") String value) {
        List<FDAccountView> accounts = fdAccountService.findAccounts(idType, value);
        if (accounts.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(accounts);
    }
    
    @PostMapping("/{accountNumber}/roles")
    public ResponseEntity<FDAccountView> addRoleToAccount(
            @PathVariable String accountNumber,
            @Valid @RequestBody AddAccountHolderRequest request) {
        FDAccountView updatedAccount = fdAccountService.addRoleToAccount(accountNumber, request);
        return ResponseEntity.ok(updatedAccount);
    }
    
    
    
    @GetMapping("/{accountNumber}/transactions")
    public ResponseEntity<List<FDTransactionView>> getAccountTransactions(@PathVariable String accountNumber) {
        List<FDTransactionView> transactions = fdAccountService.getTransactionsForAccount(accountNumber);
        return ResponseEntity.ok(transactions);
    }
    
   
   @GetMapping("/{accountNumber}/withdrawal-inquiry")
   public ResponseEntity<PrematureWithdrawalInquiryResponse> getWithdrawalInquiry( @PathVariable("accountNumber") String accountNumber) {
       PrematureWithdrawalInquiryResponse inquiryResponse = fdAccountService.getPrematureWithdrawalInquiry(accountNumber);
       return ResponseEntity.ok(inquiryResponse);
   }

   /**
    * Performs the final action of prematurely withdrawing and closing an FD account.
    */
   @PostMapping("/{accountNumber}/withdrawal")
   public ResponseEntity<FDAccountView> performEarlyWithdrawal(
		   @PathVariable("accountNumber") String accountNumber,
           @RequestBody EarlyWithdrawlRequest request) {
       FDAccountView closedAccount = fdAccountService.performEarlyWithdrawal(accountNumber, request);
       return ResponseEntity.ok(closedAccount);
   }

}
