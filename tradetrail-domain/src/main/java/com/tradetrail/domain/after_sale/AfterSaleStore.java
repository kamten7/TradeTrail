package com.tradetrail.domain.after_sale;

import java.util.Optional;

public interface AfterSaleStore {
    AfterSale create(long orderId, String customer, String reason);
    Optional<AfterSale> byOrder(long orderId);
}
