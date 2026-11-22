package com.tradetrail.persistence.after_sale;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 售后工单和审计动作的 SQL 入口，按订单保持工单唯一。
 */
public interface AfterSaleMapper extends BaseMapper<AfterSaleRow> {
    @Update("UPDATE after_sale_case SET status=#{status},decision_reason=#{decision},"
            + "return_tracking_no=#{tracking},restocked=#{restocked} WHERE id=#{id}")
    int updateCase(@Param("id") long id, @Param("status") String status, @Param("decision") String decision,
            @Param("tracking") String tracking, @Param("restocked") boolean restocked);

    @Insert("INSERT INTO after_sale_action(case_id,action,actor,reason) "
            + "VALUES(#{id},#{action},#{actor},#{reason})")
    int action(@Param("id") long id, @Param("action") String action, @Param("actor") String actor,
            @Param("reason") String reason);
}
