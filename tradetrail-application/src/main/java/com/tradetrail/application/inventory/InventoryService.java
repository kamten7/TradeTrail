package com.tradetrail.application.inventory;
import com.tradetrail.domain.TradeFailure;
import com.tradetrail.domain.inventory.StockStore;
import com.tradetrail.domain.order.OrderItem;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class InventoryService {
    private final StockStore stock;
    public InventoryService(StockStore stock){this.stock=stock;}
    @Transactional public void initialize(long product,int qty){stock.initialize(product,qty);}
    @Transactional public void adjust(long product,int delta,String reason,String operator){
        if(delta==0||reason==null||reason.isBlank()||reason.length()>300)throw new TradeFailure("INVALID_ADJUSTMENT","调整数量和原因不合法");
        if(!stock.adjust(product,delta,reason.strip(),operator))throw new TradeFailure("OUT_OF_STOCK","库存不足或商品不存在");
    }
    @Transactional public void reserve(long order,List<OrderItem> items){
        for(var item:items)if(!stock.reserve(item.productId(),item.quantity(),order))throw new TradeFailure("OUT_OF_STOCK","商品库存不足");
    }
    @Transactional public void confirm(long order,List<OrderItem> items){
        for(var item:items)if(!stock.confirm(item.productId(),item.quantity(),order))throw new IllegalStateException("库存预留确认失败");
    }
    @Transactional public void release(long order,List<OrderItem> items){
        for(var item:items)if(!stock.release(item.productId(),item.quantity(),order))throw new IllegalStateException("库存预留释放失败");
    }
    @Transactional public void restock(long order,List<OrderItem> items){
        for(var item:items)if(!stock.restock(item.productId(),item.quantity(),order))throw new TradeFailure("STOCK_ALREADY_RESTORED","库存已回补");
    }
}
