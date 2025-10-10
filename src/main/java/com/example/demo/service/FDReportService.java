package com.example.demo.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.example.demo.dto.FDAccountView;

public interface FDReportService {
	
	
	 List <FDAccountView> getAccountsMaturingWithin(int days);
	 /**
	     * Gets a list of accounts created within a specific date range.
	     * @param startDate The start of the date range.
	     * @param endDate The end of the date range.
	     * @return A list of account views.
	     */
	    List<FDAccountView> getAccountsCreatedBetween(LocalDate startDate, LocalDate endDate); 

	    List<FDAccountView> getAccountsClosedBetween(LocalDate startDate, LocalDate endDate, Optional<String> status);
}
