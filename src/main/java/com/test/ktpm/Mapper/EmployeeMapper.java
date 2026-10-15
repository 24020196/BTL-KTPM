package com.test.ktpm.Mapper;

import com.test.ktpm.Dto.Response.EmployeeResponse;
import com.test.ktpm.Entity.Employee;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class EmployeeMapper {

    public EmployeeResponse toResponse(Employee employee) {
        if (employee == null) {
            return null;
        }

        return EmployeeResponse.builder()
                .employeeNumber(employee.getEmployeeNumber())
                .employeeName(employee.getEmployeeName())
                .reportsTo(employee.getReportsTo() != null ? employee.getReportsTo().getEmployeeNumber() : null)
                .reportsToName(employee.getReportsTo() != null ? employee.getReportsTo().getEmployeeName() : null)
                .userId(employee.getUser() != null ? employee.getUser().getUserId() : null)
                .username(employee.getUser() != null ? employee.getUser().getUsername() : null)
                .build();
    }

    public List<EmployeeResponse> toResponseList(List<Employee> employees) {
        if (employees == null) {
            return Collections.emptyList();
        }
        return employees.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }
}
