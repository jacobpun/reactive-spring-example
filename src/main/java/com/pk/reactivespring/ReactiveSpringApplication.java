package com.pk.reactivespring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.ReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.ReactiveMongoTransactionManager;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;

import static org.springframework.web.reactive.function.server.RouterFunctions.*;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.ServerResponse.*;

import java.net.URI;
import java.util.Optional;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@SpringBootApplication
@AllArgsConstructor
public class ReactiveSpringApplication {
	private final CustomerRepo repo;
	private final CustomerService service;
	
	public static void main(String[] args) {
		SpringApplication.run(ReactiveSpringApplication.class, args);
	}

	@Bean
	RouterFunction<ServerResponse> routes() {
		return route()
			.GET("/customers", r -> ok().body(this.getAll(), Customer.class))
			.GET("/customers/by-name/{name}", r-> this.findByName(r))
			.GET("/customers/{id}", r-> this.findById(r))
			.POST("/customers", r -> this.createCustomer(r))
			.GET("/load", r -> ok().body(this.loadData(r.queryParam("names")), Customer.class))
			.build();
	}

	private Flux<Customer> getAll() {
		return repo.findAll();
	}

	private Mono<Customer> findByName(String name) {
		return repo.findByName(name);
	}

	private Mono<Customer> findById(String name) {
		return repo.findById(name);
	}

	private Mono<ServerResponse> createCustomer(ServerRequest req) {
		return req.bodyToMono(String.class)
					.map(name -> new Customer(null, name))
					.flatMap(c -> this.repo.save(c))
					.flatMap(c -> created(getUri(c)).body(Mono.just(c), Customer.class));
	}

	private Mono<ServerResponse> findByName(ServerRequest req) {
		return this.createResponse(this.findByName(req.pathVariable("name")));
	}
	
	private Mono<ServerResponse> findById(ServerRequest req) {
		return createResponse(this.findById(req.pathVariable("id")));
	}
	
	private Mono<ServerResponse> createResponse(Mono<Customer> customer) {
		return customer
					.flatMap(cust -> ok().body(Mono.just(cust), Customer.class))
					.switchIfEmpty(notFound().build());
	}
	
	private Flux<Customer> loadData(Optional<String> names) {
		if(names.isPresent()) {
			String namesStr = names.get();
			return service.save(namesStr.split(",")).thenMany(this.repo.findAll());	
		};
		return this.repo.findAll();
	}

	private URI getUri(Customer cust) {
		return URI.create("http://localhost:8080/customers/" + cust.getId());
	}
	
	@EventListener(ApplicationReadyEvent.class)
	public void setup() {
		// var saveCustomers$ = service.save("Martin", "Sara", "ia", "Bob");

		// saveCustomers$.thenMany(this.repo.findAll())
		// 		// .concatMap(c-> this.repo.findById(c.getId())) --> can do this instead
		// 		.subscribe(System.out::println);
	}
}


@Configuration
class Config {
	@Bean
	ReactiveTransactionManager transactionManager(ReactiveMongoDatabaseFactory cf) {
		return new ReactiveMongoTransactionManager(cf);
	}
	
	@Bean
	TransactionalOperator operator(ReactiveTransactionManager m) {
		return TransactionalOperator.create(m);
	}
}

@Service
@AllArgsConstructor
@Configuration
class CustomerService {

	private final CustomerRepo repo;
	private final TransactionalOperator operator;


	Flux<Customer> save(String... names) {
		return operator.transactional(
			Flux.fromArray(names)
				.map(name -> new Customer(null, name))
				.flatMap(repo::save)
				.doOnNext(this::validateCustomer)
		);
	}

	void validateCustomer(Customer cust) {
		char firstChar = cust.getName().charAt(0);
		Assert.isTrue(Character.isUpperCase(firstChar), "First char should be caps");
	}
}

@Repository
interface CustomerRepo extends ReactiveCrudRepository<Customer, String> {
	// @Tailable
	// Flux<Customer> findByName(String name);
	Mono<Customer> findByName(String name);
}

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document
@ToString
class Customer {
	@Id
	private String id;
	private String name;
}