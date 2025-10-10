package com.example.demo.dto;


import java.math.BigDecimal;

import com.example.demo.enums.RoleType;





public  record AccountHolderView 
(
	    String customerId,
	    RoleType roleType,
	    BigDecimal ownershipPercentage
	)

{}
