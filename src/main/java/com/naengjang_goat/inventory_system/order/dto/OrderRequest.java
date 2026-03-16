package com.naengjang_goat.inventory_system.order.dto;

import com.naengjang_goat.inventory_system.order.domain.ChannelType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record OrderRequest(
        @NotNull ChannelType channelType,
        @NotEmpty @Valid List<OrderItemRequest> items
) {}
