package com.sturdywaffle;

import com.sturdywaffle.pipeline.PipelineService;
import com.sturdywaffle.pipeline.SuggestionId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiApplicationTests {

    @Test
    void pipelineStubReturnsId() {
        PipelineService service = new PipelineService();
        SuggestionId id = service.run(new byte[0], "test.pdf");
        assertThat(id).isNotNull();
        assertThat(id.value()).isNotNull();
    }
}
