package com.uber.jenkins.phabricator.coverage;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class CoverageConverterTest {

    @Test
    public void convert() {
        List<Integer> inputCoverage = Arrays.asList(null, 2, 0, 1);
        Map<String, String> outputCoverage =
                CoverageConverter.convert(Collections.singletonMap("test", inputCoverage));
        assertEquals("NCUC", outputCoverage.get("test"));
    }
}
