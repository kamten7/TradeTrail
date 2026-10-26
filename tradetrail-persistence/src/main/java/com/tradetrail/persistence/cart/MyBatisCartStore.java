package com.tradetrail.persistence.cart;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tradetrail.domain.cart.*;
import java.util.*;
import org.springframework.stereotype.Repository;
@Repository
public class MyBatisCartStore implements CartStore {
    private final CartMapper mapper;
    public MyBatisCartStore(CartMapper mapper){this.mapper=mapper;}
    public List<CartItem> list(String customer){return mapper.selectList(new QueryWrapper<CartRow>().eq("customer",customer).orderByAsc("product_id")).stream().map(this::model).toList();}
    public Optional<CartItem> find(long id){return Optional.ofNullable(mapper.selectById(id)).map(this::model);}
    public CartItem add(String customer,long product,int quantity){
        mapper.addQuantity(customer,product,quantity);
        return model(mapper.selectOne(new QueryWrapper<CartRow>().eq("customer",customer).eq("product_id",product)));
    }
    public boolean update(long id,String customer,int qty,boolean selected){return mapper.updateOwned(id,customer,qty,selected)==1;}
    public boolean delete(long id,String customer){return mapper.deleteOwned(id,customer)==1;}
    private CartItem model(CartRow r){return new CartItem(r.id,r.customer,r.productId,r.quantity,r.selected);}
}
