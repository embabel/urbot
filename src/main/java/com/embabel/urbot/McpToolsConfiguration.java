package com.embabel.urbot;

import com.embabel.agent.api.tool.Tool;
import com.embabel.agent.spi.support.springai.SpringToolCallbackWrapper;
import io.modelcontextprotocol.client.McpSyncClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

/**
 * Discovers MCP tools from configured MCP servers and makes them
 * available as Embabel Tools for use in chat actions.
 */
@Configuration
class McpToolsConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(McpToolsConfiguration.class);

    /**
     * Strongly-typed wrapper for MCP tools discovered at startup.
     */
    public record McpTools(List<Tool> tools) {
        public McpTools {
            tools = List.copyOf(tools);
        }
    }

    @Bean
    McpTools mcpTools(ObjectProvider<List<McpSyncClient>> mcpClientsProvider) {
        var mcpClients = mcpClientsProvider.getIfAvailable();
        if (mcpClients == null || mcpClients.isEmpty()) {
            logger.debug("No MCP clients configured");
            return new McpTools(List.of());
        }

        try {
            var provider = new SyncMcpToolCallbackProvider(mcpClients);
            ToolCallback[] callbacks = provider.getToolCallbacks();
            var tools = Arrays.stream(callbacks)
                    .map(SpringToolCallbackWrapper::new)
                    .map(Tool.class::cast)
                    .toList();
            logger.info("Discovered {} MCP tools", tools.size());
            return new McpTools(tools);
        } catch (Exception e) {
            logger.warn("Failed to load MCP tools: {}", e.getMessage());
            return new McpTools(List.of());
        }
    }
}
