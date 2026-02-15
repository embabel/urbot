package com.embabel.urbot;

import com.embabel.agent.spi.support.springai.SpringAiMcpToolFactory;
import com.embabel.agent.tools.mcp.McpToolFactory;
import io.modelcontextprotocol.client.McpSyncClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Discovers MCP tools from configured MCP servers and makes them
 * available as Embabel Tools for use in chat actions.
 */
@Configuration
class McpToolsConfiguration {

    @Bean
    McpToolFactory mcpToolFactory(ObjectProvider<List<McpSyncClient>> mcpClientsProvider) {
        var mcpClients = mcpClientsProvider.getIfAvailable();
        return new SpringAiMcpToolFactory(mcpClients != null ? mcpClients : List.of());
    }
}
