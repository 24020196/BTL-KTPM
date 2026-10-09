package com.test.ktpm.Mapper;

import com.test.ktpm.Dto.Response.EmployeeDto;
import com.test.ktpm.Entity.Employee;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class EmployeeMapper {

    public EmployeeDto toDto(Employee employee) {
        if (employee == null) {
            return null;
        }

        return EmployeeDto.builder()
                .employeeNumber(employee.getEmployeeNumber())
                .employeeName(employee.getEmployeeName())
                .reportsTo(employee.getReportsTo() != null ? employee.getReportsTo().getEmployeeNumber() : null)
                .reportsToName(employee.getReportsTo() != null ? employee.getReportsTo().getEmployeeName() : null)
                .userId(employee.getUser() != null ? employee.getUser().getUserId() : null)
                .username(employee.getUser() != null ? employee.getUser().getUsername() : null)
                .build();
    }

    public List<EmployeeDto> toDtoList(List<Employee> employees) {
        if (employees == null) {
            return Collections.emptyList();
        }
        return employees.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }
}
