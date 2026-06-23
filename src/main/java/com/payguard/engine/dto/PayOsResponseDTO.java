package com.payguard.engine.dto;

import java.util.List;

public class PayOsResponseDTO {
    private String code;
    private String desc;
    private List<PayOsTransactionDTO> data;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    public List<PayOsTransactionDTO> getData() {
        return data;
    }

    public void setData(List<PayOsTransactionDTO> data) {
        this.data = data;
    }
}