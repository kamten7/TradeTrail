package com.tradetrail.application.catalog;
import com.tradetrail.application.inventory.InventoryService;
import com.tradetrail.application.integration.CommandService;
import com.tradetrail.domain.TradeFailure;
import com.tradetrail.domain.catalog.*;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class CatalogService {
    private final CatalogStore store;
    private final InventoryService stock;
    private final CommandService commands;
    public CatalogService(CatalogStore store,InventoryService stock,CommandService commands){this.store=store;this.stock=stock;this.commands=commands;}
    @Transactional public Product create(String name,String description,long price,int qty){
        validate(name,description,price);if(qty<0)throw new TradeFailure("INVALID_PRODUCT","库存不能为负");
        var product=store.create(name.strip(),description==null?"":description,price,qty);
        stock.initialize(product.id(),qty);return get(product.id(),true);
    }
    public Product get(long id,boolean drafts){
        var p=store.find(id).orElseThrow(()->new TradeFailure("PRODUCT_NOT_FOUND","商品不存在"));
        if(!drafts&&!p.published())throw new TradeFailure("PRODUCT_NOT_FOUND","商品不存在");return p;
    }
    public List<Product> list(boolean drafts){return store.list(drafts);}
    @Transactional public Product publish(long id){lockQuote(id);if(!store.publish(id))throw new TradeFailure("PRODUCT_NOT_FOUND","商品不存在");return get(id,true);}
    @Transactional public Product update(long id,String name,String description,long price,boolean published){
        validate(name,description,price);lockQuote(id);
        if(!store.update(id,name.strip(),description==null?"":description,price,published))throw new TradeFailure("PRODUCT_NOT_FOUND","商品不存在");
        return get(id,true);
    }
    @Transactional public Product lockQuote(long id){commands.lock("catalog:"+id,"catalog");return get(id,true);}
    private void validate(String name,String description,long price){
        if(name==null||name.isBlank()||name.length()>120||price<1||price>100_000_000L||description!=null&&description.length()>1000)
            throw new TradeFailure("INVALID_PRODUCT","商品名称、价格或描述不合法");
    }
}
