package com.callibrity.mocapi.mcp.protocol;

public record ClientCapabilities(RootsCapability roots, SamplingCapability sampling, ElicitationCapability elicitation) {
}
