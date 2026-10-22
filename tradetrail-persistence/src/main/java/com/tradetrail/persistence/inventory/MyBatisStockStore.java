package com.tradetrail.persistence.inventory;

import com.tradetrail.domain.inventory.StockStore;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisStockStore implements StockStore {
    private final StockMapper mapper;
    private final StockLedgerMapper ledger;
    private final StockAdjustmentMapper adjustments;

    public MyBatisStockStore(StockMapper mapper, StockLedgerMapper ledger, StockAdjustmentMapper adjustments) {
        this.mapper = mapper;
        this.ledger = ledger;
        this.adjustments = adjustments;
    }

    @Override
    public void initialize(long productId, int quantity) {
        StockRow row = new StockRow();
        row.productId = productId;
        row.available = quantity;
        row.reserved = 0;
        row.sold = 0;
        row.version = 0;
        mapper.insert(row);
    }

    @Override
    public boolean adjust(long productId, int delta, String reason, String operator) {
        if (mapper.adjust(productId, delta) != 1) return false;
        adjustments.add(productId, delta, reason, operator);
        return true;
    }

    @Override
    public boolean reserve(long productId, int quantity, long orderId) {
        if (mapper.reserve(productId, quantity) != 1) return false;
        ledger.add(orderId, productId, quantity, "RESERVE");
        return true;
    }

    @Override
    public boolean confirm(long productId, int quantity, long orderId) {
        if (mapper.confirm(productId, quantity) != 1) return false;
        ledger.add(orderId, productId, quantity, "CONFIRM");
        return true;
    }

    @Override
    public boolean release(long productId, int quantity, long orderId) {
        if (mapper.release(productId, quantity) != 1) return false;
        ledger.add(orderId, productId, quantity, "RELEASE");
        return true;
    }
}
