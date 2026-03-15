package org.springframework.ai.chat.client.advisor.api;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import reactor.core.publisher.Flux;

import java.util.List;

public interface StreamAdvisorChain extends AdvisorChain {

    /**
     * Invokes the next {@link StreamAdvisor} in the {@link StreamAdvisorChain} with the
     * given request.
     */
    Flux<ChatClientResponse> nextStream(ChatClientRequest chatClientRequest);

    /**
     * Returns the list of all the {@link StreamAdvisor} instances included in this chain
     * at the time of its creation.
     */
    List<StreamAdvisor> getStreamAdvisors();

    /**
     * Creates a new StreamAdvisorChain copy that contains all advisors after the
     * specified advisor.
     * @param after the StreamAdvisor after which to copy the chain
     * @return a new StreamAdvisorChain containing all advisors after the specified
     * advisor
     * @throws IllegalArgumentException if the specified advisor is not part of the chain
     */
    StreamAdvisorChain copy(StreamAdvisor after);
}
