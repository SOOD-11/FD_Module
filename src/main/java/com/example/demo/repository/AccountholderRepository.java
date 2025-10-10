package com.example.demo.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.demo.entities.Accountholder;

@Repository
public interface AccountholderRepository extends JpaRepository<Accountholder, Long> {

    /**
     * Finds all account relationships for a specific customer.
     * WHY: Useful for a customer dashboard to show all accounts they are linked to
     * (as owner, nominee, etc.) without needing to load the full account details.
     */
    List<Accountholder> findByCustomerId(String customerId);
}