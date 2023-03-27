package com.capillary.spar.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Objects;

@Data
public class SparStockAndPriceDataImport {
    private String id;
    private String sku;
    private String stock;
    private String locationCode;
    private String mrp;
    private String webPrice;
    private String productDetails;
    private String status;
    private String fileName;

    @Override
    public String toString() {
        return "id: " + id + ", sku: " + sku + ", stock: " + stock + ", locationCode: " + locationCode +
               ", mrp: " + mrp + ", web price: " + webPrice + ", filename: " + fileName + ", status: " + status;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SparStockAndPriceDataImport that = (SparStockAndPriceDataImport) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
