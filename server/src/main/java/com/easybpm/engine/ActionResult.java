package com.easybpm.engine;

public record ActionResult(String instanceId, String status, String currentNodeName) {
}
