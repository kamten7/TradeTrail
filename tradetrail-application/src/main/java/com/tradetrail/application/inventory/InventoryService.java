package com.tradetrail.application.inventory;

import com.tradetrail.domain.TradeFailure;
import com.tradetrail.domain.inventory.StockStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {
    private final StockStore stock;

    public InventoryService(StockStore stock) {
        this.stock = stock;
    }

    @Transactional
    public void adjust(long productId, int delta, String reason, String operator) {
        if (delta == 0 || reason == null || reason.isBlank()) {
            throw new TradeFailure("INVALID_ADJUSTMENT", "调整数量和原因不能为空");
        }
        if (!stock.adjust(productId, delta, reason.strip(), operator)) {
            throw new TradeFailure("OUT_OF_STOCK", "库存不足或商品不存在");
        }
    }
}
