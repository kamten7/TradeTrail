package com.tradetrail.persistence.payment;
import com.tradetrail.domain.payment.*;
import org.apache.ibatis.annotations.*;
public interface PaymentMapper {
    @Select("SELECT id,order_id,channel,amount_cents,status,provider_status FROM payment_attempt WHERE order_id=#{id}") PaymentAttempt byOrder(long id);
    @Select("SELECT id,order_id,channel,amount_cents,status,provider_status FROM payment_attempt WHERE id=#{id}") PaymentAttempt find(String id);
    @Insert("INSERT INTO payment_attempt(id,order_id,channel,amount_cents,status,provider_status) VALUES(#{id},#{orderId},#{channel},#{amountCents},#{status},#{providerStatus})") int create(PaymentAttempt a);
    @Update("UPDATE payment_attempt SET provider_status=#{status} WHERE id=#{id}") int provider(@Param("id") String id,@Param("status") String status);
    @Update("UPDATE payment_attempt SET status=#{status} WHERE id=#{id}") int status(@Param("id") String id,@Param("status") String status);
    @Select("SELECT id,payment_id,order_id,case_id,amount_cents,status,reason,attempts,last_error FROM refund_attempt WHERE id=#{id}") RefundAttempt refund(String id);
    @Select("SELECT id,payment_id,order_id,case_id,amount_cents,status,reason,attempts,last_error FROM refund_attempt WHERE payment_id=#{id}") RefundAttempt refundForPayment(String id);
    @Insert("INSERT INTO refund_attempt(id,payment_id,order_id,case_id,amount_cents,status,reason) VALUES(#{id},#{paymentId},#{orderId},#{caseId},#{amountCents},#{status},#{reason})") int createRefund(RefundAttempt r);
    @Update("UPDATE refund_attempt SET status=#{status},last_error=#{error},attempts=attempts+1 WHERE id=#{id}") int refundStatus(@Param("id") String id,@Param("status") String status,@Param("error") String error);
}
