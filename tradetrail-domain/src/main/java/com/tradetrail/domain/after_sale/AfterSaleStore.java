package com.tradetrail.domain.after_sale;

import java.util.List;
import java.util.Optional;

public interface AfterSaleStore {
    AfterSale create(long order, String customer, String reason, String type);

    Optional<AfterSale> byOrder(long order);

    Optional<AfterSale> find(long id);

    List<AfterSale> list();

    void update(long id, String status, String decision, String tracking, boolean restocked);

    void action(long id, String action, String actor, String reason);
}
