package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.List;
import java.util.Optional;

@SpringBootApplication
@EnableScheduling  // ⭐ Enable @Scheduled support for daily transfer
public class DemoApplication {

	public static void main(String[] args) {

		List<List<Integer>> numbers = List.of(List.of(1,2), List.of(3,4));

		numbers.stream()
				.flatMap(list -> list.stream())   // flatten
				.forEach(System.out::println);


		SpringApplication.run(DemoApplication.class, args);
	}

}
