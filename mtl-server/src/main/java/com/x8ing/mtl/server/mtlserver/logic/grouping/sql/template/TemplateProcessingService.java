package com.x8ing.mtl.server.mtlserver.logic.grouping.sql.template;

import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.TemplateSpec;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;


/**
 * select id from gps_track gt where start_date BETWEEN TO_TIMESTAMP(:DATE_FROM, 'YYYY-MM-DD HH24:MI:SS') AND TO_TIMESTAMP(:DATE_TO, 'YYYY-MM-DD HH24:MI:SS') order by start_date
 * INTERSECT
 * [[@{/GPS_TRACK/StandardFilter}]]
 * INTERSECT
 * [# th:include="/GPS_TRACK/StandardFilter"][/]
 */

@Service
public class TemplateProcessingService {

    private final TemplateEngine templateEngine;

    // for variables in Thymeleaf TEXT syntax, e.g. [(${userName})]
    //private static final String PARAM_REGEXP = "\\[\\(\\$\\{([^}]+)}\\)]";


    public TemplateProcessingService(CustomTemplateResolver customTemplateResolver) {
        this.templateEngine = new TemplateEngine();
        this.templateEngine.setTemplateResolver(customTemplateResolver);
        customTemplateResolver.setTemplateMode(TemplateMode.TEXT);
    }

    public String processTemplate(String templatePath) {

        Context context = new Context();

        //context.setVariables(params);

        // Process the template using the TemplateEngine and return the result
        // issue: does not carry template mode...
        // return templateEngine.process(templatePath, context);
        //TemplateSpec spec = new TemplateSpec(templatePath, TemplateMode.TEXT); // mode TEXT won't be passed from Resolver if not given in spec... bug?
        TemplateSpec spec = new TemplateSpec(templatePath, "text/plain"); // Still hacky XXX TODO

        return templateEngine.process(spec, context);
    }


//    /**
//     * Extract all variables in the format ${PARAM} from the SQL string.
//     */
//    public Set<String> extractVariables(String template) {
//        Set<String> variables = new HashSet<>();
//
//        // Regular expression to find variables in the format ${PARAM}
//        Pattern pattern = Pattern.compile(PARAM_REGEXP);
//        Matcher matcher = pattern.matcher(template);
//
//        while (matcher.find()) {
//            variables.add(matcher.group(1)); // Add the variable name
//        }
//
//        return variables;
//    }

}
