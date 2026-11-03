package org.example.secondhandtradingplatform.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.secondhandtradingplatform.payment.entity.PaymentRecord;

@Mapper
public interface PaymentRecordMapper extends BaseMapper<PaymentRecord> {
}
