///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 21+
//JAVAC_OPTIONS -parameters
//DEPS org.springframework.boot:spring-boot-dependencies:4.0.8@pom
//DEPS org.springframework.ai:spring-ai-bom:2.0.1@pom
//DEPS org.springframework.ai:spring-ai-starter-mcp-server-webmvc
//JAVA_OPTIONS -Dserver.port=8060
//JAVA_OPTIONS -Dspring.ai.mcp.server.name=dvara-demo-tools
//JAVA_OPTIONS -Dspring.ai.mcp.server.version=1.0.0
//JAVA_OPTIONS -Dspring.ai.mcp.server.protocol=STREAMABLE
//JAVA_OPTIONS -Dspring.main.banner-mode=off

// A demo MCP server for the DVARA MCP quickstart: three tools, one of each kind the
// gateway governs differently. Run it with `jbang DemoMcpServer.java`; it serves
// Streamable HTTP on http://localhost:8060/mcp.

package demo;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

@SpringBootApplication
public class DemoMcpServer {

    public static void main(String[] args) {
        SpringApplication.run(DemoMcpServer.class, args);
    }

    @Bean
    ToolCallbackProvider demoTools(DemoTools tools) {
        return MethodToolCallbackProvider.builder().toolObjects(tools).build();
    }

    @Service
    static class DemoTools {

        @Tool(name = "get_order_status", description = "Look up the shipping status of an order.")
        public String getOrderStatus(@ToolParam(description = "the order id, e.g. A-1001") String orderId) {
            return "Order " + orderId + ": shipped, arriving Thursday.";
        }

        // The result carries an email address, a phone number and a card number, so the
        // gateway has something to redact before the agent sees it.
        @Tool(name = "lookup_customer", description = "Fetch a customer's contact record by customer id.")
        public String lookupCustomer(@ToolParam(description = "the customer id, e.g. C-42") String customerId) {
            return """
                Customer %s: Jane Doe, jane.doe@example.com, +1 415 555 0134,
                card on file 4111 1111 1111 1111, plan Enterprise.""".formatted(customerId);
        }

        // Irreversible. A policy denies it, so the gateway refuses the call and this method
        // never runs; if it does run, the line below says so in the server log.
        @Tool(name = "delete_customer", description = "Permanently delete a customer record. Irreversible.")
        public String deleteCustomer(@ToolParam(description = "the customer id to delete") String customerId) {
            System.out.println("delete_customer(" + customerId + ") EXECUTED");
            return "Deleted customer " + customerId + ".";
        }
    }
}
