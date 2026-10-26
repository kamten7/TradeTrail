package com.tradetrail.api;

import com.tradetrail.application.after_sale.AfterSaleService;
import com.tradetrail.application.cart.CartService;
import com.tradetrail.application.catalog.CatalogService;
import com.tradetrail.application.fulfillment.FulfillmentService;
import com.tradetrail.application.order.OrderService;
import com.tradetrail.application.payment.PaymentService;
import com.tradetrail.domain.TradeFailure;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.junit.jupiter.api.Assertions.*;

class TransactionRegressionTest extends ApiTestBase {
    @Autowired CatalogService catalog;
    @Autowired OrderService orders;
    @Autowired CartService cart;
    @Autowired PaymentService payments;
    @Autowired AfterSaleService cases;
    @Autowired FulfillmentService fulfillment;

    @Test void acceptedAfterSaleBlocksShipment() {
        long product = productWithStock(10);
        var order = orders.create("customer", product, 1, UUID.randomUUID().toString());
        payments.completeMockPayment(order.id(), "customer");
        cases.apply(order.id(), "customer", "不再需要");
        assertThrows(TradeFailure.class, () -> fulfillment.ship(order.id()));
    }

    @Test void concurrentSameRequestReturnsOneOrder() throws Exception {
        long product = productWithStock(10);
        String key = UUID.randomUUID().toString();
        try (var executor = Executors.newFixedThreadPool(8)) {
            var calls = IntStream.range(0, 8).<Callable<Long>>mapToObj(i ->
                    () -> orders.create("customer", product, 1, key).id()).toList();
            var results = executor.invokeAll(calls);
            long first = results.getFirst().get();
            for (var result : results) assertEquals(first, result.get());
        }
        assertEquals(9, catalog.get(product, true).available());
    }

    @Test void concurrentCartAddsPreserveEveryIncrement() throws Exception {
        long product = productWithStock(100);
        String customer = UUID.randomUUID().toString();
        cart.add(customer, product, 1);
        try (var executor = Executors.newFixedThreadPool(8)) {
            var calls = IntStream.range(0, 20).<Callable<Void>>mapToObj(i -> () -> {
                cart.add(customer, product, 1);
                return null;
            }).toList();
            for (var result : executor.invokeAll(calls)) result.get();
        }
        assertEquals(21, cart.list(customer).getFirst().quantity());
    }

    private long productWithStock(int stock) {
        var product = catalog.create("回归商品", "测试", 100, stock);
        catalog.publish(product.id());
        return product.id();
    }
}
