package com.portscape.api.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/** A rede vem do scan indicado, nao do pedido: assim nao ha forma de os desalinhar. */
public record PinBaselineRequest(@NotNull UUID scanId) {
}
