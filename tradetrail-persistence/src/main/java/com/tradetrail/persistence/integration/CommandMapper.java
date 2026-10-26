package com.tradetrail.persistence.integration;
import com.tradetrail.domain.integration.CommandStore.Receipt;
import org.apache.ibatis.annotations.*;
public interface CommandMapper {
    @Insert("INSERT INTO idempotency_record(command_key,fingerprint) VALUES(#{key},#{hash}) ON DUPLICATE KEY UPDATE command_key=command_key")
    int acquire(@Param("key") String key, @Param("hash") String hash);
    @Select("SELECT fingerprint,response_status,response_body FROM idempotency_record WHERE command_key=#{key} FOR UPDATE")
    Receipt receipt(String key);
    @Update("UPDATE idempotency_record SET response_status=#{status},response_body=#{body} WHERE command_key=#{key}")
    int complete(@Param("key") String key,@Param("status") int status,@Param("body") String body);
}
