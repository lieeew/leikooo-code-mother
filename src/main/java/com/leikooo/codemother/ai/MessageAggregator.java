package com.leikooo.codemother.ai;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * @author leikooo
 */
@Slf4j
public class MessageAggregator {

    public static Flux<String> aggregateString(Flux<String> flux, StringBuilder resultCollector) {
        return aggregateString(flux, response -> false, resultCollector);
    }

    public static Flux<String> aggregateString(Flux<String> flux, Predicate<String> windowClosePredicate, StringBuilder resultCollector) {
        return flux.windowUntil(windowClosePredicate.negate())
                .flatMap(Flux::collectList)
                .filter(list -> !list.isEmpty())
                .map(list -> String.join("", list))
                .doOnNext(resultCollector::append);
    }

    public static void aggregateAndCollect(Flux<String> flux, Consumer<String> onComplete) {
        StringBuilder result = new StringBuilder();
        aggregateString(flux, result)
                .subscribe();
        onComplete.accept(result.toString());
    }
}
