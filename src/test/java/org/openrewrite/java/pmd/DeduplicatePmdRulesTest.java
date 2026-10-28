/*
 * Copyright 2026 the original author or authors.
 * <p>
 * Licensed under the Moderne Source Available License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://docs.moderne.io/licensing/moderne-source-available-license
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.pmd;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.xml.Assertions.xml;

class DeduplicatePmdRulesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new DeduplicatePmdRules());
    }

    @DocumentExample
    @Test
    void deduplicateReferencesKeepingPositionAndComments() {
        rewriteRun(
          xml(
            """
              <ruleset name="custom">
                  <rule ref="category/java/codestyle.xml/ControlStatementBraces"/>
                  <!-- Other rules -->
                  <rule ref="category/java/errorprone.xml/EmptyCatchBlock"/>
                  <!-- More braces -->
                  <rule ref="category/java/codestyle.xml/ControlStatementBraces"/>
                  <rule ref="category/java/codestyle.xml/ControlStatementBraces"/>
              </ruleset>
              """,
            """
              <ruleset name="custom">
                  <rule ref="category/java/codestyle.xml/ControlStatementBraces"/>
                  <!-- Other rules -->
                  <rule ref="category/java/errorprone.xml/EmptyCatchBlock"/>
                  <!-- More braces -->
              </ruleset>
              """
          )
        );
    }

    @Test
    void mergeConfigurationIntoPlainReference() {
        rewriteRun(
          xml(
            """
              <ruleset name="custom">
                  <rule ref="category/java/codestyle.xml/ControlStatementBraces"/>
                  <rule ref="category/java/codestyle.xml/ControlStatementBraces" message="Use braces">
                      <priority>2</priority>
                      <properties>
                          <property name="checkIfStmt" value="false"/>
                      </properties>
                  </rule>
              </ruleset>
              """,
            """
              <ruleset name="custom">
                  <rule ref="category/java/codestyle.xml/ControlStatementBraces" message="Use braces">
                      <priority>2</priority>
                      <properties>
                          <property name="checkIfStmt" value="false"/>
                      </properties>
                  </rule>
              </ruleset>
              """
          )
        );
    }

    @Test
    void mergeExplicitSettingsWithLaterValuesTakingPrecedence() {
        rewriteRun(
          xml(
            """
              <ruleset name="custom">
                  <rule ref="category/java/codestyle.xml/ControlStatementBraces" message="First" externalInfoUrl="https://example.org">
                      <description>First description</description>
                      <priority>1</priority>
                      <properties>
                          <!-- Keep this setting -->
                          <property name="checkWhileStmt" value="false"/>
                          <property name="checkIfStmt" value="true"/>
                      </properties>
                      <example>if (a) b();</example>
                  </rule>
                  <rule ref="category/java/codestyle.xml/ControlStatementBraces" message="Last">
                      <description>Last description</description>
                      <priority>2</priority>
                      <properties>
                          <!-- Override this setting -->
                          <property name="checkIfStmt">
                              <value><![CDATA[false]]></value>
                          </property>
                          <property name="checkForStmt" value="false"/>
                      </properties>
                      <example>for (;;) break;</example>
                  </rule>
              </ruleset>
              """,
            """
              <ruleset name="custom">
                  <rule ref="category/java/codestyle.xml/ControlStatementBraces" message="Last" externalInfoUrl="https://example.org">
                      <description>Last description</description>
                      <priority>2</priority>
                      <properties>
                          <!-- Keep this setting -->
                          <property name="checkWhileStmt" value="false"/>
                          <!-- Override this setting -->
                          <property name="checkIfStmt">
                              <value><![CDATA[false]]></value>
                          </property>
                          <property name="checkForStmt" value="false"/>
                      </properties>
                      <example>if (a) b();</example>
                      <example>for (;;) break;</example>
                  </rule>
              </ruleset>
              """
          )
        );
    }

    @Test
    void doNotMergeWholeRulesetImportsOrDistinctRules() {
        rewriteRun(
          xml(
            """
              <ruleset name="custom">
                  <rule ref="category/java/codestyle.xml">
                      <exclude name="ControlStatementBraces"/>
                  </rule>
                  <rule ref="category/java/codestyle.xml">
                      <exclude name="UnnecessaryImport"/>
                  </rule>
                  <rule ref="category/java/codestyle.xml/ControlStatementBraces"/>
                  <rule ref="mycorp/rules.xml/ControlStatementBraces"/>
                  <rule ref="category/java/codestyle.xml/ControlStatementBraces" name="CustomBraces"/>
                  <rule name="ControlStatementBraces" class="mycorp.CustomRule"/>
              </ruleset>
              """
          )
        );
    }

    @Test
    void doNotChangeNonRulesetXml() {
        rewriteRun(
          xml(
            """
              <project>
                  <ruleset>
                      <rule ref="category/java/codestyle.xml/ControlStatementBraces"/>
                      <rule ref="category/java/codestyle.xml/ControlStatementBraces"/>
                  </ruleset>
              </project>
              """
          )
        );
    }

    @Test
    void doNotChangeXmlWithoutRootElement() {
        rewriteRun(
          xml("<!-- No root element -->")
        );
    }

    @Test
    void doNotChangeEmptyRuleset() {
        rewriteRun(
          xml("<ruleset/>")
        );
    }
}
