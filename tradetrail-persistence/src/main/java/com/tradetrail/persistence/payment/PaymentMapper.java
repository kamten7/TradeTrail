package com.tradetrail.persistence.payment;

import com.tradetrail.domain.payment.PaymentAttempt;
import com.tradetrail.domain.payment.RefundAttempt;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface PaymentMapper {
    @Select("SELECT id,order_id,channel,amount_cents,status,provider_status FROM payment_attempt "
            + "WHERE order_id=#{id}")
    PaymentAttempt byOrder(long id);

    @Select("SELECT id,order_id,channel,amount_cents,status,provider_status FROM payment_attempt WHERE id=#{id}")
    PaymentAttempt find(String id);

    @Insert("INSERT INTO payment_attempt(id,order_id,channel,amount_cents,status,provider_status) "
            + "VALUES(#{id},#{orderId},#{channel},#{amountCents},#{status},#{providerStatus})")
    int create(PaymentAttempt a);

    @Update("UPDATE payment_attempt SET provider_status=#{status} WHERE id=#{id}")
    int provider(@Param("id") String id, @Param("status") String status);

    @Update("UPDATE payment_attempt SET status=#{status} WHERE id=#{id}")
    int status(@Param("id") String id, @Param("status") String status);

    @Select("SELECT id,payment_id,order_id,case_id,amount_cents,status,reason,attempts,last_error "
            + "FROM refund_attempt WHERE id=#{id}")
    RefundAttempt refund(String id);

    @Select("SELECT id,payment_id,order_id,case_id,amount_cents,status,reason,attempts,last_error "
            + "FROM refund_attempt WHERE payment_id=#{id}")
    RefundAttempt refundForPayment(String id);

    @Insert("INSERT INTO refund_attempt(id,payment_id,order_id,case_id,amount_cents,status,reason) "
            + "VALUES(#{id},#{paymentId},#{orderId},#{caseId},#{amountCents},#{status},#{reason})")
    int createRefund(RefundAttempt r);

    @Select("SELECT id,payment_id,order_id,case_id,amount_cents,status,reason,attempts,last_error "
            + "FROM refund_attempt WHERE status='FAILED' ORDER BY order_id DESC")
    List<RefundAttempt> failedRefunds();

    @Update("UPDATE refund_attempt SET status=#{status},last_error=#{error},attempts=attempts+CASE "
            + "WHEN #{status} IN ('SUCCEEDED','FAILED') THEN 1 ELSE 0 END WHERE id=#{id}")
    int refundStatus(@Param("id") String id, @Param("status") String status, @Param("error") String error);
}
