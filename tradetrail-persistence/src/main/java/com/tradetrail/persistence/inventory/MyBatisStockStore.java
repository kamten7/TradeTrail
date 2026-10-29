package com.tradetrail.persistence.inventory;

import com.tradetrail.domain.inventory.StockStore;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisStockStore implements StockStore {
    private final StockMapper mapper;
    private final StockLedgerMapper ledger;
    private final StockAdjustmentMapper adjustments;
    private final ReservationMapper reservations;

    public MyBatisStockStore(StockMapper mapper, StockLedgerMapper ledger, StockAdjustmentMapper adjustments,
            ReservationMapper reservations) {
        this.mapper = mapper;
        this.ledger = ledger;
        this.adjustments = adjustments;
        this.reservations = reservations;
    }

    public void initialize(long product, int qty) {
        StockRow row = new StockRow();
        row.productId = product;
        row.available = qty;
        row.reserved = 0;
        row.sold = 0;
        row.version = 0;
        mapper.insert(row);
        adjustments.add(product, qty, "商品初始库存", "merchant");
    }

    public boolean adjust(long product, int delta, String reason, String operator) {
        if (mapper.adjust(product, delta) != 1) {
            return false;
        }
        adjustments.add(product, delta, reason, operator);
        return true;
    }

    public boolean reserve(long product, int qty, long order) {
        if (mapper.reserve(product, qty) != 1) {
            return false;
        }
        reservations.reserve(order, product, qty);
        ledger.add(order, product, qty, "RESERVE");
        return true;
    }

    public boolean confirm(long product, int qty, long order) {
        if (reservations.transition(order, product, qty, "RESERVED", "CONFIRMED") != 1) {
            return false;
        }
        if (mapper.confirm(product, qty) != 1) {
            throw new IllegalStateException("预留库存账本不一致");
        }
        ledger.add(order, product, qty, "CONFIRM");
        return true;
    }

    public boolean release(long product, int qty, long order) {
        if (reservations.transition(order, product, qty, "RESERVED", "RELEASED") != 1) {
            return false;
        }
        if (mapper.release(product, qty) != 1) {
            throw new IllegalStateException("预留库存账本不一致");
        }
        ledger.add(order, product, qty, "RELEASE");
        return true;
    }

    public boolean restock(long product, int qty, long order) {
        if (reservations.transition(order, product, qty, "CONFIRMED", "RESTOCKED") != 1) {
            return false;
        }
        if (mapper.restock(product, qty) != 1) {
            throw new IllegalStateException("已售库存账本不一致");
        }
        ledger.add(order, product, qty, "RESTOCK");
        return true;
    }
}
