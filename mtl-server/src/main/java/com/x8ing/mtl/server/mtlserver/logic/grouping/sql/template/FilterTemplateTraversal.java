package com.x8ing.mtl.server.mtlserver.logic.grouping.sql.template;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

final class FilterTemplateTraversal {

    private FilterTemplateTraversal() {
    }

    static void enter(Deque<String> stack, String templatePath) {
        if (stack.contains(templatePath)) {
            List<String> cycle = new ArrayList<>(stack);
            cycle.add(templatePath);
            throw new IllegalStateException(
                    "Detected a cycle in filter template includes: " + String.join(" -> ", cycle));
        }
        stack.addLast(templatePath);
    }

    static void leave(Deque<String> stack) {
        stack.removeLast();
    }
}
