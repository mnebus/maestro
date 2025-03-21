package org.example.activity.impl;

import org.example.activity.model.ProductInventory;
import org.example.activity.interfaces.InventoryActivity;
import org.example.workflow.model.OrderedProduct;

import java.util.ArrayList;
import java.util.List;

public class InventoryActivityImpl implements InventoryActivity {
    @Override
    public List<OrderedProduct> reserveInventory(List<OrderedProduct> orderedProducts) {
        System.out.println("reserving inventory");
        return new ArrayList<>(orderedProducts);
    }

    @Override
    public List<ProductInventory> decreaseInventory(List<OrderedProduct> orderedProducts) {
        System.out.println("decreasing inventory");

        return orderedProducts.stream()
                .map(orderedProduct -> new ProductInventory(orderedProduct.name(), (int) (Math.random() * 100)))
                .toList();
    }
}
