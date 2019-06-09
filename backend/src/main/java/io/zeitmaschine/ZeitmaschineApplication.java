package io.zeitmaschine;

import io.zeitmaschine.index.IndexerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.http.MediaType.TEXT_HTML;
import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.accept;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@SpringBootApplication
public class ZeitmaschineApplication {

    // https://github.com/spring-projects/spring-boot/issues/9785
    @Value("classpath:/static/index.html")
    private Resource indexHtml;

    @Bean
    public RouterFunction<ServerResponse> routes() {
        // https://github.com/spring-projects/spring-boot/issues/9785
        return route(
                GET("/").and(accept(TEXT_HTML)), request -> ServerResponse.ok().syncBody(indexHtml))
                .andRoute(GET("/login").and(accept(TEXT_HTML)), request -> ServerResponse.ok().syncBody(indexHtml));
    }

    @Bean
    public RouteLocator apiGateway(RouteLocatorBuilder builder, IndexerConfig indexConfig) {
        return builder.routes()
                .route("elasticsearch", r -> r.path("/zeitmaschine/**")
                        .uri(indexConfig.getHost()))
                .build();
    }

    public static void main(String[] args) {
        SpringApplication.run(ZeitmaschineApplication.class, args);
    }
}
