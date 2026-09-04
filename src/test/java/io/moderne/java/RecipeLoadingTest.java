package io.moderne.java;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

class RecipeLoadingTest implements RewriteTest {

    @Test
    void loadRecipeFromFile() {
        assertRecipesConfigure();
    }
}
