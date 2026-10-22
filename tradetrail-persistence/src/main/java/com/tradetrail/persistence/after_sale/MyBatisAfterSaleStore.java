package com.tradetrail.persistence.after_sale;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tradetrail.domain.after_sale.AfterSale;
import com.tradetrail.domain.after_sale.AfterSaleStore;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisAfterSaleStore implements AfterSaleStore {
    private final AfterSaleMapper mapper;

    public MyBatisAfterSaleStore(AfterSaleMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public AfterSale create(long orderId, String customer, String reason) {
        AfterSaleRow row = new AfterSaleRow();
        row.orderId = orderId;
        row.customer = customer;
        row.reason = reason;
        row.status = "APPLIED";
        mapper.insert(row);
        return model(row);
    }

    @Override
    public Optional<AfterSale> byOrder(long orderId) {
        return Optional.ofNullable(mapper.selectOne(new QueryWrapper<AfterSaleRow>().eq("order_id", orderId)))
                .map(this::model);
    }

    private AfterSale model(AfterSaleRow row) {
        return new AfterSale(row.id, row.orderId, row.customer, row.reason, row.status);
    }
}
