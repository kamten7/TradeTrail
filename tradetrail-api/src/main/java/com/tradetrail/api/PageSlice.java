package com.tradetrail.api;
import com.tradetrail.domain.TradeFailure;
import java.util.List;
public final class PageSlice {
    private PageSlice(){}
    public static <T> List<T> of(List<T> values,int page,int size){
        if(page<0||size<1||size>100)throw new TradeFailure("INVALID_PAGE","分页参数不合法");
        long from=(long)page*size;
        return from>=values.size()?List.of():values.subList((int)from,(int)Math.min(from+size,values.size()));
    }
}
