package com.pk.reactivespring;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import static org.mockito.Mockito.*;

@SpringBootTest
class ReactiveSpringApplicationTests {

	@Mock
	CustomerRepo repo;

	@Test
	void contextLoads() {
	}

	@Test
	public void shouldGetCustomerById() {
		Customer cust = new Customer("foo", "Foo");
		when(repo.findById("foo")).thenReturn(Mono.just(cust));

		var testClient = WebTestClient.bindToRouterFunction(new ReactiveSpringApplication(repo, null).routes()).build();
		testClient.get()
					.uri("/customers/foo")
					.exchange()
					.expectStatus()
						.isOk()
					.expectBody(Customer.class)
						.isEqualTo(cust);
	}

	@Test
	public void shouldSaveCustomer() {
		var testCustomerName = "foo";
		Customer unsaved = new Customer(null, testCustomerName);
		Customer saved = new Customer("id-01", testCustomerName);
		when(repo.save(unsaved)).thenReturn(Mono.just(saved));

		var testClient = WebTestClient.bindToRouterFunction(new ReactiveSpringApplication(repo, null).routes()).build();
		
		testClient
			.post()
			.uri("/customers")
			.contentType(MediaType.TEXT_XML)
			.bodyValue(testCustomerName)
			.exchange()
			.expectStatus()
				.isCreated()
			.expectBody(Customer.class)
				.isEqualTo(saved);
	}
}