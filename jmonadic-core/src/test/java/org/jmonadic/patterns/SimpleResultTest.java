package org.jmonadic.patterns;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Simple Result Tests")
class SimpleResultTest {

    @Test
    @DisplayName("Should create and use successful Result")
    void shouldCreateAndUseSuccessfulResult() {
        Result<String, String> result = Result.success("hello");
        
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isFailure()).isFalse();
        assertThat(result.getValue()).isEqualTo("hello");
    }
    
    @Test
    @DisplayName("Should create and use failed Result")
    void shouldCreateAndUseFailedResult() {
        Result<String, String> result = Result.failure("error");
        
        assertThat(result.isFailure()).isTrue();
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).isEqualTo("error");
    }
    
    @Test
    @DisplayName("Should map successful values")
    void shouldMapSuccessfulValues() {
        Result<Integer, String> result = Result.<String, String>success("5")
            .map(Integer::valueOf);
        
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getValue()).isEqualTo(5);
    }
    
    @Test
    @DisplayName("Should not map failed values")
    void shouldNotMapFailedValues() {
        Result<Integer, String> result = Result.<String, String>failure("error")
            .map(Integer::valueOf);
        
        assertThat(result.isFailure()).isTrue();
        assertThat(result.getError()).isEqualTo("error");
    }
    
    @Test
    @DisplayName("Should flat map successfully")
    void shouldFlatMapSuccessfully() {
        Result<Integer, String> result = Result.<String, String>success("5")
            .flatMap(s -> Result.success(Integer.valueOf(s)));
        
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getValue()).isEqualTo(5);
    }
    
    @Test
    @DisplayName("Should handle fold operation")
    void shouldHandleFoldOperation() {
        String successResult = Result.success("value")
            .fold(value -> "Success: " + value, error -> "Error: " + error);
        
        String failureResult = Result.<String, String>failure("error")
            .fold(value -> "Success: " + value, error -> "Error: " + error);
        
        assertThat(successResult).isEqualTo("Success: value");
        assertThat(failureResult).isEqualTo("Error: error");
    }
}