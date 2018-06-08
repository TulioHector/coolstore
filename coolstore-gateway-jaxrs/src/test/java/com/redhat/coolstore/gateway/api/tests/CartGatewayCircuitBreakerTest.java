package com.redhat.coolstore.gateway.api.tests;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.netflix.config.ConfigurationManager;
import com.netflix.hystrix.Hystrix;
import com.netflix.hystrix.HystrixCircuitBreaker;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.exception.HystrixRuntimeException;
import com.redhat.coolstore.gateway.api.CartGateway;
import com.redhat.coolstore.gateway.proxy.CartResource;

@Category(UnitTests.class)
public class CartGatewayCircuitBreakerTest {

	@Mock
	private CartResource proxy;

	@Before
	public void before() throws Exception {
		MockitoAnnotations.initMocks(this);
		resetHystrix();
		warmUpGetCartCircuitBreaker();
		openCircuitBreakerAfterOneFailingRequest();

	}

	@Test
	public void testGetCartCircuitBreaker() throws Exception {
		String cartId = "1";
		doThrow(RuntimeException.class).when(proxy).getCart(cartId);
		HystrixCircuitBreaker circuitBreaker = getCircuitBreaker();

		// demonstrates circuit is actually closed
		assertThat(circuitBreaker.isOpen(), equalTo(false));
		assertThat(circuitBreaker.allowRequest(), equalTo(true));

		CartGateway.GetCartCommand command = new CartGateway.GetCartCommand(proxy, cartId);
		try {
			command.execute();
			fail();
		} catch (HystrixRuntimeException e) {
            waitUntilCircuitBreakerOpens();
			circuitBreaker = getCircuitBreaker();
			assertThat(circuitBreaker.isOpen(), is(true));
		}
	}

	private void waitUntilCircuitBreakerOpens() throws InterruptedException {
		Thread.sleep(1000);
	}

	private void resetHystrix() {
		Hystrix.reset();
	}

	private void warmUpGetCartCircuitBreaker() {
		when(proxy.getCart("1")).thenReturn(null);
		CartGateway.GetCartCommand command = new CartGateway.GetCartCommand(proxy, "1");
		command.execute();
	}

	public static HystrixCircuitBreaker getCircuitBreaker() {
		return HystrixCircuitBreaker.Factory.getInstance(getCommandKey());
	}

	private static HystrixCommandKey getCommandKey() {
		return HystrixCommandKey.Factory.asKey(CartGateway.GetCartCommand.GET_CART_COMMAND_KEY);
	}

	private void openCircuitBreakerAfterOneFailingRequest() {
		ConfigurationManager.getConfigInstance().setProperty(
				"hystrix.command." + CartGateway.GetCartCommand.GET_CART_COMMAND_KEY + ".circuitBreaker.requestVolumeThreshold", 1);
	}

}
