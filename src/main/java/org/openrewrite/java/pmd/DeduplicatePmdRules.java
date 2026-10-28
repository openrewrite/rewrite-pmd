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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Content;
import org.openrewrite.xml.tree.Xml;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Value
@EqualsAndHashCode(callSuper = false)
public class DeduplicatePmdRules extends Recipe {
    String displayName = "Deduplicate PMD rule references";

    String description = "Merges duplicate references to individual rules in PMD ruleset XML files, keeping the first " +
            "reference's position and combining explicit configuration. Later explicit values take precedence for " +
            "conflicting properties, attributes, descriptions and priorities. Whole-ruleset imports are left alone " +
            "because their exclusions may enable different rules.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Document visitDocument(Xml.Document document, ExecutionContext ctx) {
                Xml.Tag root = document.getRoot();
                if (root == null || !"ruleset".equals(root.getName()) || root.getContent() == null) {
                    return document;
                }
                List<Content> content = new ArrayList<>();
                Map<List<String>, Integer> references = new HashMap<>();
                boolean changed = false;
                for (Content child : root.getContent()) {
                    if (child instanceof Xml.Tag && "rule".equals(((Xml.Tag) child).getName())) {
                        Xml.Tag rule = (Xml.Tag) child;
                        String ref = PmdRuleRef.attributeValue(rule, "ref");
                        if (ref != null && !ref.endsWith(".xml")) {
                            String name = PmdRuleRef.attributeValue(rule, "name");
                            Integer index = references.putIfAbsent(
                                    Arrays.asList(ref, name == null ? PmdRuleRef.name(ref) : name), content.size());
                            if (index != null) {
                                content.set(index, merge((Xml.Tag) content.get(index), rule));
                                changed = true;
                                continue;
                            }
                        }
                    }
                    content.add(child);
                }
                return changed ? document.withRoot(root.withContent(content)) : document;
            }
        };
    }

    private static Xml.Tag merge(Xml.Tag first, Xml.Tag later) {
        List<Xml.Attribute> attributes = new ArrayList<>(first.getAttributes());
        for (Xml.Attribute attribute : later.getAttributes()) {
            int index = 0;
            while (index < attributes.size() &&
                   !attributes.get(index).getKeyAsString().equals(attribute.getKeyAsString())) {
                index++;
            }
            if (index < attributes.size()) {
                Xml.Attribute existing = attributes.get(index);
                attributes.set(index, existing.withValue(attribute.getValue()));
            } else {
                attributes.add(attribute);
            }
        }
        Xml.Tag merged = first.withAttributes(attributes);
        if (later.getContent() == null || later.getContent().isEmpty()) {
            return merged;
        }
        List<Content> content = new ArrayList<>();
        if (first.getContent() != null) {
            content.addAll(first.getContent());
        }
        List<String> order = Arrays.asList("description", "priority", "properties", "exclude", "example");
        List<Content> pending = new ArrayList<>();
        for (Content child : later.getContent()) {
            if (!(child instanceof Xml.Tag)) {
                pending.add(child);
                continue;
            }
            Xml.Tag tag = (Xml.Tag) child;
            String name = tag.getName();
            int index = 0;
            for (; index < content.size(); index++) {
                if (!(content.get(index) instanceof Xml.Tag)) {
                    continue;
                }
                Xml.Tag existing = (Xml.Tag) content.get(index);
                if (name.equals(existing.getName()) &&
                    ("properties".equals(name) || "priority".equals(name) || "description".equals(name) ||
                     "property".equals(name) && PmdRuleRef.attributeValue(tag, "name") != null &&
                     PmdRuleRef.attributeValue(tag, "name").equals(PmdRuleRef.attributeValue(existing, "name")))) {
                    content.addAll(index, pending);
                    index += pending.size();
                    pending.clear();
                    content.set(index, "properties".equals(name) ? merge(existing, tag) : tag.withPrefix(existing.getPrefix()));
                    break;
                }
            }
            if (index == content.size()) {
                int insertion = content.size();
                if ("rule".equals(first.getName()) && order.contains(name)) {
                    for (int i = 0; i < content.size(); i++) {
                        if (content.get(i) instanceof Xml.Tag &&
                            order.indexOf(((Xml.Tag) content.get(i)).getName()) > order.indexOf(name)) {
                            insertion = i;
                            break;
                        }
                    }
                }
                content.addAll(insertion, pending);
                content.add(insertion + pending.size(), tag);
                pending.clear();
            }
        }
        content.addAll(pending);
        return merged.withContent(content);
    }
}
