package com.tradetrail.domain.after_sale;

import java.util.List;
import java.util.Optional;

/**
 * 定义售后数据访问端口，使业务规则不依赖数据库实现。
 */
public interface AfterSaleStore {
    AfterSale create(long order, String customer, String reason, String type);

    Optional<AfterSale> byOrder(long order);

    Optional<AfterSale> find(long id);

    List<AfterSale> list();

    void update(long id, String status, String decision, String tracking, boolean restocked);

    void action(long id, String action, String actor, String reason);
}
