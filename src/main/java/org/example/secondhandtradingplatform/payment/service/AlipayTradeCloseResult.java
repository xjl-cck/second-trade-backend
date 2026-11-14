package org.example.secondhandtradingplatform.payment.service;

public record AlipayTradeCloseResult(Status status, String outTradeNo) {

    public enum Status {
        CLOSED,
        PAID,
        UNKNOWN
    }

    public static AlipayTradeCloseResult closed(String outTradeNo) {
        return new AlipayTradeCloseResult(Status.CLOSED, outTradeNo);
    }

    public static AlipayTradeCloseResult paid(String outTradeNo) {
        return new AlipayTradeCloseResult(Status.PAID, outTradeNo);
    }

    public static AlipayTradeCloseResult unknown(String outTradeNo) {
        return new AlipayTradeCloseResult(Status.UNKNOWN, outTradeNo);
    }
}
