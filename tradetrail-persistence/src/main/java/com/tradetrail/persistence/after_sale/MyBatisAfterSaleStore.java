package com.tradetrail.persistence.after_sale;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tradetrail.domain.after_sale.*;
import java.util.*;
import org.springframework.stereotype.Repository;
@Repository
public class MyBatisAfterSaleStore implements AfterSaleStore {
    private final AfterSaleMapper mapper;
    public MyBatisAfterSaleStore(AfterSaleMapper mapper){this.mapper=mapper;}
    public AfterSale create(long order,String customer,String reason,String type){
        var row=new AfterSaleRow();row.orderId=order;row.customer=customer;row.reason=reason;row.status="APPLIED";row.type=type;row.restocked=false;
        mapper.insert(row);return find(row.id).orElseThrow();
    }
    public Optional<AfterSale> byOrder(long id){return Optional.ofNullable(mapper.selectOne(new QueryWrapper<AfterSaleRow>().eq("order_id",id))).map(this::model);}
    public Optional<AfterSale> find(long id){return Optional.ofNullable(mapper.selectById(id)).map(this::model);}
    public List<AfterSale> list(){return mapper.selectList(new QueryWrapper<AfterSaleRow>().orderByDesc("id")).stream().map(this::model).toList();}
    public void update(long id,String status,String decision,String tracking,boolean restocked){mapper.updateCase(id,status,decision,tracking,restocked);}
    public void action(long id,String action,String actor,String reason){mapper.action(id,action,actor,reason);}
    private AfterSale model(AfterSaleRow r){return new AfterSale(r.id,r.orderId,r.customer,r.reason,r.status,r.type,r.decisionReason,r.returnTrackingNo,r.restocked,r.createdAt);}
}
