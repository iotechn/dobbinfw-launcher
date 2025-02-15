package com.dobbinsoft.fw.launcher.inter;

import com.dobbinsoft.fw.core.exception.ServiceException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;


public interface BeforeFileUpload {

    public Mono<Void> before(ServerWebExchange request) throws ServiceException;

}
