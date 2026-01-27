
package com.example.userservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import javax.annotation.PostConstruct;

import com.example.userservice.model.Employee;
import com.example.userservice.service.EmployeeService;
import com.example.userservice.repository.EmployeeRepository;

import java.util.stream.Collector;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.IntSummaryStatistics;
import java.util.List;

@RestController
@RequestMapping("/employees")
public class EmployeeController {
    private final EmployeeService employeeService;

    private final EmployeeRepository employeeRepository;

    @PostConstruct
    public void init() {
        System.out.println("EmployeeController loaded!");
    }

    @Autowired
    public EmployeeController(EmployeeService employeeService, EmployeeRepository employeeRepository) {
        this.employeeService = employeeService; 
        this.employeeRepository = employeeRepository;
    }

    @PostMapping
    public ResponseEntity<Employee> createEmployee(@RequestBody Employee employee) {
        Employee savedEmployee = employeeService.saveEmployee(employee);
        return new ResponseEntity<>(savedEmployee, HttpStatus.CREATED);
    }

    @Operation(summary = "Get all employees")
    @GetMapping
    public ResponseEntity<List<Employee>> getAllEmployees() {
        List<Employee> employees = employeeService.getAllEmployees();
        return new ResponseEntity<>(employees, HttpStatus.OK);
    }

    @Operation(summary = "Get all employee ages statistics")
    @GetMapping("/ages/statistics")
    public ResponseEntity<?> getAllEmployeeAgesStatistics() {
       List<Integer> ages = employeeRepository.findAll().stream().map(employee -> employee.getAge()).collect(Collectors.toList());
       IntSummaryStatistics stats = ages.stream().mapToInt(x->x).summaryStatistics();
       return ResponseEntity.ok(stats.getAverage());
               
    }

     @Operation(summary = "Get sort by ages")
    @GetMapping("/ages/sort")
    public ResponseEntity<?> getAllSortByAges() {
      // List<Integer> ages = employeeRepository.findAll().stream().map(employee -> employee.getAge()).sorted().collect(Collectors.toList());
       //List<Integer> sortedAges = ages.stream().skip(1).limit(2).collect(Collectors.toList());
         List<String> namesList = employeeRepository.findAll().stream().map(employee -> employee.getName()).collect(Collectors.toList());
         String namesListUpperCaseJoined = namesList.stream().map(name -> name.toUpperCase()).collect(Collectors.joining(", "));
       return ResponseEntity.ok(namesListUpperCaseJoined);
               
    }

    @GetMapping("/{id}")
    public ResponseEntity<Employee> getEmployeeById(@PathVariable String id) {
        Employee employee = employeeService.getEmployeeById(id);
        if (employee != null) {
            return new ResponseEntity<>(employee, HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }
}
