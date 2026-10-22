package com.tradetrail.domain.catalog;

public record Product(Long id, String name, String description, long priceCents, boolean published, int available) {
}
