package com.example.demo.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.dto.AccountHolderView;
import com.example.demo.dto.AddAccountHolderRequest;
import com.example.demo.dto.CreateFDAccountRequest;
import com.example.demo.dto.EarlyWithdrawlRequest;
import com.example.demo.dto.FDAccountView;
import com.example.demo.dto.FDTransactionView;
import com.example.demo.dto.PrematureWithdrawalInquiryResponse;
import com.example.demo.entities.Accountholder;
import com.example.demo.entities.FdAccount;
import com.example.demo.entities.FdTransaction;

import com.example.demo.enums.AccountStatus;
import com.example.demo.enums.MaturityInstruction;
import com.example.demo.enums.RoleType;
import com.example.demo.enums.TransactionType;
import com.example.demo.events.AccountClosedEvent;
import com.example.demo.events.AccountCreatedEvent;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.repository.FdAccountRepository;
import com.example.demo.repository.FdTransactionRepository;
import com.example.demo.service.FDAccountService;
import com.example.demo.service.KafkaProducerService;
import com.example.demo.util.AccountNumberGenerator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@Slf4j

@Service

@Transactional

@RequiredArgsConstructor
public class FDAccountServiceImpl implements FDAccountService{
	
	private final FdAccountRepository fdAccountRepository ;
    private final AccountNumberGenerator accountNumberGenerator ;
private final KafkaProducerService kafkaProducerService;
private final FdTransactionRepository transactionRepository;

    public FDAccountView createAccount(CreateFDAccountRequest request) {
        log.info("Creating new FD account for customer: {}", request.customerId());

        // Step 2: Handle default vs. custom values
        Integer term = Objects.requireNonNullElse(request.termInMonths(), 12); // Default 12 months
        BigDecimal rate = Objects.requireNonNullElse(request.interestRate(), new BigDecimal("5.00")); // Default 5.00%
        // TODO: In a real app, validate custom term/rate against product limits

        // Step 3: Generate account number
        String accountNumber = accountNumberGenerator.generate();

        // Step 4: Calculations
        LocalDate effectiveDate = LocalDate.now();
        LocalDate maturityDate = effectiveDate.plusMonths(term);
        BigDecimal maturityAmount = calculateMaturityAmount(request.principalAmount(), rate, term);

        // Step 5: Build FDAccount entity
        FdAccount fdAccount = new FdAccount();
        fdAccount.setAccountNumber(accountNumber);
        fdAccount.setAccountName(request.accountName());
        fdAccount.setProductCode(request.productCode());
        fdAccount.setStatus(AccountStatus.ACTIVE);
        fdAccount.setTermInMonths(term);
        fdAccount.setInterestRate(rate);
        fdAccount.setPrincipalAmount(request.principalAmount());
        fdAccount.setMaturityAmount(maturityAmount);
        fdAccount.setEffectiveDate(effectiveDate);
        fdAccount.setMaturityDate(maturityDate);
        fdAccount.setMaturityInstruction(MaturityInstruction.PAYOUT_TO_LINKED_ACCOUNT); // Default instruction

        // Step 6: Build AccountHolder entity
        Accountholder owner = new Accountholder();
        owner.setCustomerId(request.customerId());
        owner.setRoleType(RoleType.OWNER);
        owner.setOwnershipPercentage(new BigDecimal("100.00"));

        // Step 7: Build initial Transaction entity
        FdTransaction initialDeposit = new FdTransaction();
        initialDeposit.setTransactionType(TransactionType.PRINCIPAL_DEPOSIT);
        initialDeposit.setAmount(request.principalAmount());
        initialDeposit.setTransactionDate(LocalDateTime.now());
        initialDeposit.setTransactionReference(UUID.randomUUID().toString());
        initialDeposit.setDescription("Initial principal deposit.");

        // Step 8: Link entities together
        fdAccount.setAccountHolders(List.of(owner));
        fdAccount.setTransactions(List.of(initialDeposit));
        owner.setFdAccount(fdAccount);
        initialDeposit.setFdAccount(fdAccount);

        // Step 9: Save to database
        FdAccount savedAccount = fdAccountRepository.save(fdAccount);
        log.info("Successfully created FD account with number: {}", savedAccount.getAccountNumber());

        // Step 10: Publish Kafka event
        AccountCreatedEvent event = new AccountCreatedEvent(
                savedAccount.getAccountNumber(),
                request.customerId(),
                savedAccount.getPrincipalAmount(),
                savedAccount.getMaturityDate(),
                UUID.randomUUID().toString()
        );
      kafkaProducerService.sendAccountCreatedEvent(event);

        // Step 11: Map to DTO and return
        return mapToView(savedAccount);
    }

    public FDAccountView findAccountByNumber(String accountNumber) {
        FdAccount account = fdAccountRepository.findByAccountNumber(accountNumber)
                .orElseThrow();
        return mapToView(account);
    }
    @Override
    public List<FDAccountView> findAccounts(String idType, String value) {
        log.info("Searching for accounts with type: {} and value: {}", idType, value);
        List<FdAccount> accounts;

        switch (idType.toUpperCase()) {
            case "ACCOUNT_NUMBER":
                accounts = fdAccountRepository.findByAccountNumber(value)
                        .map(List::of) // Convert Optional<FDAccount> to List<FDAccount>
                        .orElse(List.of()); // Or an empty list if not found
                break;
            case "CUSTOMER_ID":
                accounts = fdAccountRepository.findAccountsByCustomerId(value);
                break;
            case "ACCOUNT_NAME":
                accounts = fdAccountRepository.findByAccountNameContainingIgnoreCase(value);
                break;
            default:
                throw new IllegalArgumentException("Unsupported search idType: " + idType);
        }

        // Map the list of entities to a list of DTOs and return
        return accounts.stream()
                .map(this::mapToView) // Reuse our existing mapping method
                .collect(Collectors.toList());
    }
    
    // to add accountholder roles like nominee 
    @Override
    @Transactional // Ensure this is a transactional method
    public FDAccountView addRoleToAccount(String accountNumber, AddAccountHolderRequest request) {
        log.info("Adding role {} for customer {} to account {}", request.roleType(), request.customerId(), accountNumber);

        // 1. Find the account we want to modify
        FdAccount account = fdAccountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with number: " + accountNumber));

        // 2. Create the new AccountHolder entity from the request
        Accountholder newHolder = new Accountholder();
        newHolder.setCustomerId(request.customerId());
        newHolder.setRoleType(request.roleType());
        newHolder.setOwnershipPercentage(request.ownershipPercentage());
        newHolder.setFdAccount(account); // Set the back-reference to the parent account

        // 3. Add the new holder to the account's list of holders
        account.getAccountHolders().add(newHolder);

        // 4. Save the parent account. Due to CascadeType.ALL, this will also save the new holder.
        FdAccount updatedAccount = fdAccountRepository.save(account);

        // 5. Map the updated entity to a DTO and return it
        return mapToView(updatedAccount);
    }
    
    
    
    
    @Override
    public List<FDTransactionView> getTransactionsForAccount(String accountNumber) {
        log.info("Fetching transactions for account number: {}", accountNumber);

        // First, ensure the account actually exists. This is good practice.
        fdAccountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with number: " + accountNumber));

        // Call the repository method to get the transaction entities
        List<FdTransaction> transactions = transactionRepository.findByFdAccount_AccountNumberOrderByTransactionDateDesc(accountNumber);

        // Map the list of entities to a list of DTOs
        return transactions.stream()
                .map(transaction -> new FDTransactionView(
                        transaction.getTransactionType(),
                        transaction.getAmount(),
                        transaction.getTransactionDate(),
                        transaction.getDescription(),
                        transaction.getTransactionReference()
                ))
                .collect(Collectors.toList());
    }
    public BigDecimal calculateMaturityAmount(BigDecimal principal, BigDecimal rate, Integer termInMonths) {
        // Simple Interest Formula: A = P(1 + rt)
        // r = annual rate / 100, t = term in years
        BigDecimal rateAsDecimal = rate.divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP);
        BigDecimal termInYears = new BigDecimal(termInMonths).divide(new BigDecimal("12"), 10, RoundingMode.HALF_UP);
        BigDecimal interest = principal.multiply(rateAsDecimal).multiply(termInYears);
        return principal.add(interest).setScale(2, RoundingMode.HALF_UP);
    }

    private FDAccountView mapToView(FdAccount account) {
        List<AccountHolderView> holderViews = account.getAccountHolders().stream()
                .map(holder -> new AccountHolderView(
                        holder.getCustomerId(),
                        holder.getRoleType(),
                        holder.getOwnershipPercentage()
                )).collect(Collectors.toList());

        return new FDAccountView(
                account.getAccountNumber(),
                account.getAccountName(),
                account.getProductCode(),
                account.getStatus(),
                account.getPrincipalAmount(),
                account.getMaturityAmount(),
                account.getEffectiveDate(),
                account.getMaturityDate(),
                holderViews
        );
    }
    
    @Transactional
    public FDAccountView performEarlyWithdrawal(String accountNumber, EarlyWithdrawlRequest request) {
        log.info("Performing early withdrawal for account: {} with reason: {}", accountNumber, request.reason());

        PrematureWithdrawalInquiryResponse inquiry = getPrematureWithdrawalInquiry(accountNumber);
        FdAccount account = fdAccountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with number: " + accountNumber));

        FdTransaction penaltyTransaction = new FdTransaction();
        penaltyTransaction.setFdAccount(account);
        penaltyTransaction.setTransactionType(TransactionType.PENALTY_DEBIT);
        penaltyTransaction.setAmount(inquiry.penaltyAmount());
        penaltyTransaction.setTransactionDate(LocalDateTime.now());
        penaltyTransaction.setDescription("Penalty for premature withdrawal.");
        penaltyTransaction.setTransactionReference(UUID.randomUUID().toString());

        FdTransaction withdrawalTransaction = new FdTransaction();
        withdrawalTransaction.setFdAccount(account);
        withdrawalTransaction.setTransactionType(TransactionType.PREMATURE_WITHDRAWAL);
        withdrawalTransaction.setAmount(inquiry.finalPayoutAmount());
        withdrawalTransaction.setTransactionDate(LocalDateTime.now());
        withdrawalTransaction.setDescription("Premature withdrawal payout.");
        withdrawalTransaction.setTransactionReference(UUID.randomUUID().toString());
        
        account.getTransactions().add(penaltyTransaction);
        account.getTransactions().add(withdrawalTransaction);

        account.setStatus(AccountStatus.PREMATURELY_CLOSED);
        account.setClosedAt(LocalDateTime.now());

        FdAccount closedAccount = fdAccountRepository.save(account);

        List<String> customerIdsToNotify = account.getAccountHolders().stream()
                .map(Accountholder::getCustomerId)
                .collect(Collectors.toList());

        AccountClosedEvent event = new AccountClosedEvent(
            accountNumber, "PREMATURE_WITHDRAWAL", inquiry.finalPayoutAmount(), LocalDate.now(), UUID.randomUUID().toString(), customerIdsToNotify);
        kafkaProducerService.sendAccountClosedEvent(event);

        return mapToView(closedAccount);
    }
    
    
   
    
    
    
    
    @Override
    public PrematureWithdrawalInquiryResponse getPrematureWithdrawalInquiry(String accountNumber) {
        log.info("Performing premature withdrawal inquiry for account: {}", accountNumber);
        FdAccount account = fdAccountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with number: " + accountNumber));

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalArgumentException("Inquiry can only be performed on ACTIVE accounts.");
        }
        
        LocalDate today = LocalDate.now();
        LocalDate effectiveDate = account.getEffectiveDate();
        long daysActive = ChronoUnit.DAYS.between(effectiveDate, today);

        if (daysActive <= 0) {
            return new PrematureWithdrawalInquiryResponse(
                accountNumber, account.getPrincipalAmount(), BigDecimal.ZERO, BigDecimal.ZERO, account.getPrincipalAmount(), today);
        }
        
        BigDecimal penaltyInterestRate = account.getInterestRate().subtract(new BigDecimal("1.00"));
        if (penaltyInterestRate.compareTo(BigDecimal.ZERO) < 0) {
            penaltyInterestRate = BigDecimal.ZERO;
        }

        BigDecimal originalDailyRate = account.getInterestRate().divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP).divide(new BigDecimal("365"), 10, RoundingMode.HALF_UP);
        BigDecimal penaltyDailyRate = penaltyInterestRate.divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP).divide(new BigDecimal("365"), 10, RoundingMode.HALF_UP);

        BigDecimal originalInterestAccrued = account.getPrincipalAmount().multiply(originalDailyRate).multiply(new BigDecimal(daysActive)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal penalizedInterestAccrued = account.getPrincipalAmount().multiply(penaltyDailyRate).multiply(new BigDecimal(daysActive)).setScale(2, RoundingMode.HALF_UP);
        
        BigDecimal penaltyAmount = originalInterestAccrued.subtract(penalizedInterestAccrued);
        BigDecimal finalPayoutAmount = account.getPrincipalAmount().add(penalizedInterestAccrued);

        return new PrematureWithdrawalInquiryResponse(
            accountNumber, account.getPrincipalAmount(), penalizedInterestAccrued, penaltyAmount, finalPayoutAmount, today);
    }

}
