package cz.spojenka.lwt.cicomock;

import cz.spojenka.lwt.cicomock.controllers.ClientWSHandler;
import cz.spojenka.lwt.cicomock.controllers.ServerWSHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ClientWSHandler clientWSHandler;
    private final ServerWSHandler serverWSHandler;

    public WebSocketConfig(ClientWSHandler clientWSHandler, ServerWSHandler serverWSHandler) {
        this.clientWSHandler = clientWSHandler;
        this.serverWSHandler = serverWSHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry
                .addHandler(clientWSHandler, "/client")
                .addHandler(serverWSHandler, "/server")
                .setAllowedOrigins("*");
    }
}
