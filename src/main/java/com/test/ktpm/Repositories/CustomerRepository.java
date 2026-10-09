package com.test.ktpm.Repositories;

import com.test.ktpm.Entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    @Query("SELECT c FROM Customer c WHERE c.salesRepEmployee.employeeNumber = :employeeNumber")
    List<Customer> findBySalesRepEmployeeNumber(@Param("employeeNumber") Long employeeNumber);
}
