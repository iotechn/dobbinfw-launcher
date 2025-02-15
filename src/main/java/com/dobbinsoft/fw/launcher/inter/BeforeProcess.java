package com.dobbinsoft.fw.launcher.inter;

import com.dobbinsoft.fw.core.exception.ServiceException;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public interface BeforeProcess {

    public Mono<Void> before(ServerWebExchange exchange) throws ServiceException;

}
