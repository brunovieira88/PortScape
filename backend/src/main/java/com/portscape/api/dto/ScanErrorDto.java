package com.portscape.api.dto;

/** Motivo da falha de um scan, presente apenas quando o estado e FAILED. */
public record ScanErrorDto(String code, String message) {
}
