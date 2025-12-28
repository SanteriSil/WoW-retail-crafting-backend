package com.crafting.blizz;

public class AuctionEntry {
    private final long unitPrice;
    private final int quantity;

    public AuctionEntry(long unitPrice, int quantity) {
        this.unitPrice = unitPrice;
        this.quantity = quantity;
    }

    public long getUnitPrice() {
        return unitPrice;
    }

    public int getQuantity() {
        return quantity;
    }
    @Override public String toString() {
        return "{unitPrice=" + unitPrice + ", qty=" + quantity + "}";
    }
}