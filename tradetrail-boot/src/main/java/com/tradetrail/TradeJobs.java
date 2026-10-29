package com.tradetrail;

import com.tradetrail.application.fulfillment.FulfillmentService;
import com.tradetrail.application.integration.OutboxDispatcher;
import com.tradetrail.application.order.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "tradetrail.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class TradeJobs {
    private static final Logger LOG = LoggerFactory.getLogger(TradeJobs.class);
    private final OutboxDispatcher outbox;
    private final OrderService orders;
    private final FulfillmentService fulfillment;
    private final int days;

    public TradeJobs(OutboxDispatcher outbox, OrderService orders, FulfillmentService fulfillment,
            @Value("${tradetrail.auto-confirm-days:7}") int days) {
        this.outbox = outbox;
        this.orders = orders;
        this.fulfillment = fulfillment;
        this.days = days;
    }

    @Scheduled(fixedDelayString = "${tradetrail.jobs.delay-ms:2000}")
    public void dispatch() {
        outbox.dispatch();
    }

    @Scheduled(fixedDelayString = "${tradetrail.jobs.lifecycle-delay-ms:10000}")
    public void lifecycle() {
        for (long id : orders.expired()) {
            try {
                orders.expire(id);
            } catch (Exception e) {
                LOG.error("订单超时处理失败 orderId={}", id, e);
            }
        }
        long cursor = 0;
        while (true) {
            var batch = fulfillment.autoConfirmDue(days, cursor);
            if (batch.isEmpty()) {
                break;
            }
            for (long id : batch) {
                try {
                    orders.autoConfirm(id);
                } catch (Exception e) {
                    LOG.error("自动确认失败 orderId={}", id, e);
                }
            }
            cursor = batch.getLast();
        }
    }
}
